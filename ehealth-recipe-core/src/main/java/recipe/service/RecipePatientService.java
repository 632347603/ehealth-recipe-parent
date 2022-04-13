package recipe.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.ngari.base.property.service.IConfigurationCenterUtilsService;
import com.ngari.common.mode.HisResponseTO;
import com.ngari.his.base.PatientBaseInfo;
import com.ngari.his.patient.mode.HisCardVO;
import com.ngari.his.patient.mode.PatientQueryRequestTO;
import com.ngari.his.recipe.mode.ChronicDiseaseListReqTO;
import com.ngari.his.recipe.mode.ChronicDiseaseListResTO;
import com.ngari.his.recipe.mode.PatientChronicDiseaseRes;
import com.ngari.patient.dto.PatientDTO;
import com.ngari.patient.service.PatientService;
import com.ngari.patient.utils.ObjectCopyUtils;
import com.ngari.recipe.common.RecipeResultBean;
import com.ngari.recipe.drugsenterprise.model.DepDetailBean;
import com.ngari.recipe.drugsenterprise.model.DepListBean;
import com.ngari.recipe.dto.GiveModeButtonDTO;
import com.ngari.recipe.dto.GiveModeShowButtonDTO;
import com.ngari.recipe.entity.*;
import com.ngari.recipe.recipe.model.RankShiftList;
import com.ngari.recipe.recipe.model.RecipeDetailBean;
import com.ngari.recipe.vo.*;
import com.ngari.revisit.common.model.RevisitExDTO;
import ctd.controller.exception.ControllerException;
import ctd.dictionary.DictionaryController;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcBean;
import ctd.util.annotation.RpcService;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import recipe.ApplicationUtils;
import recipe.bean.DrugEnterpriseResult;
import recipe.caNew.pdf.CreatePdfFactory;
import recipe.client.OperationClient;
import recipe.client.PatientClient;
import recipe.client.RevisitClient;
import recipe.common.CommonConstant;
import recipe.constant.*;
import recipe.core.api.patient.IOfflineRecipeBusinessService;
import recipe.core.api.patient.IPatientBusinessService;
import recipe.dao.*;
import recipe.drugsenterprise.RemoteDrugEnterpriseService;
import recipe.enumerate.type.CheckPatientEnum;
import recipe.enumerate.type.MedicalTypeEnum;
import recipe.enumerate.type.RecipeSupportGiveModeEnum;
import recipe.hisservice.RecipeToHisService;
import recipe.manager.OrganDrugListManager;
import recipe.manager.RecipeDetailManager;
import recipe.manager.RecipeManager;
import recipe.service.common.RecipeCacheService;
import recipe.util.RedisClient;
import recipe.util.ValidateUtil;
import recipe.vo.doctor.RecipeInfoVO;

import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 患者端服务
 * company: ngarihealth
 *
 * @author: 0184/yu_yun
 * @date:2017/6/30.
 */
@RpcBean("recipePatientService")
public class RecipePatientService extends RecipeBaseService implements IPatientBusinessService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RecipePatientService.class);
    @Autowired
    private ChronicDiseaseDAO chronicDiseaseDAO;
    @Autowired
    private PatientClient patientClient;
    @Autowired
    private RevisitClient revisitClient;
    @Autowired
    private RecipeParameterDao recipeParameterDao;
    @Autowired
    private RecipeManager recipeManager;
    @Autowired
    private OrganDrugListManager organDrugListManager;
    @Autowired
    private RecipeDetailManager recipeDetailManager;
    @Autowired
    private CreatePdfFactory createPdfFactory;
    @Autowired
    private IOfflineRecipeBusinessService offlineRecipeBusinessService;
    @Autowired
    private OperationClient operationClient;

    /**
     * 根据取药方式过滤药企
     *
     * @param recipeIds
     * @param payModes
     * @return
     */
    @RpcService
    public RecipeResultBean filterSupportDepList(List<Integer> recipeIds, final List<Integer> payModes) {
        RecipeResultBean result = this.findSupportDepList(1, recipeIds);
        RecipeResultBean backResult = RecipeResultBean.getSuccess();
        backResult.setCode(result.getCode());
        backResult.setMsg(result.getMsg());
        Object depListObj = result.getObject();
        DepListBean newBean = new DepListBean();
        if (null != depListObj && depListObj instanceof DepListBean) {
            DepListBean depListBean = (DepListBean) depListObj;
            newBean.setList(Lists.newArrayList(Collections2.filter(depListBean.getList(), new Predicate<DepDetailBean>() {
                @Override
                public boolean apply(@Nullable DepDetailBean input) {
                    if (payModes.contains(input.getPayMode())) {
                        return true;
                    }
                    return false;
                }
            })));
        }
        backResult.setObject(newBean);

        return backResult;
    }

    /**
     * 获取供应商列表
     *
     * @param findDetail 1:表示获取详情，0：表示判断是否需要展示供应商具体列表-开处方时查询库存
     * @param recipeIds
     */
    @RpcService
    public RecipeResultBean findSupportDepList(int findDetail, List<Integer> recipeIds) {
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        RecipeDetailDAO detailDAO = DAOFactory.getDAO(RecipeDetailDAO.class);
        RecipeService recipeService = ApplicationUtils.getRecipeService(RecipeService.class);
        RemoteDrugEnterpriseService remoteDrugService = ApplicationUtils.getRecipeService(RemoteDrugEnterpriseService.class);
        RecipeCacheService cacheService = ApplicationUtils.getRecipeService(RecipeCacheService.class);

        RecipeResultBean resultBean = RecipeResultBean.getSuccess();
        List<Recipe> recipeList = recipeDAO.findByRecipeIds(recipeIds);
        if (CollectionUtils.isEmpty(recipeList)) {
            resultBean.setCode(RecipeResultBean.FAIL);
            resultBean.setMsg("处方不存在");
            return resultBean;
        }

        DepListBean depListBean = new DepListBean();
        Integer organId = recipeList.get(0).getClinicOrgan();
        BigDecimal totalMoney = BigDecimal.ZERO;
        for (Recipe recipe : recipeList) {
            if (!recipe.getClinicOrgan().equals(organId)) {
                resultBean.setCode(RecipeResultBean.FAIL);
                resultBean.setMsg("选择处方的机构不一致，请重新选择");
                return resultBean;
            }

            totalMoney = totalMoney.add(recipe.getTotalMoney());
        }

        List<DrugsEnterprise> depList = recipeService.findSupportDepList(recipeIds, organId, null, false, null);
        LOGGER.info("findSupportDepList recipeIds={}, 匹配到药企数量[{}]", JSONUtils.toString(recipeIds), depList.size());
        if (CollectionUtils.isNotEmpty(depList)) {
            //设置默认值
            depListBean.setSigle(true);
            //只需要查询是否存在多个供应商
            if (0 == findDetail && depList.size() > 1) {
                depListBean.setSigle(false);
                resultBean.setObject(depListBean);
                //开处方阶段---判断药企库存这里就返回了
                return resultBean;
            }

            //该详情数据包含了所有处方的详情，可能存在同一种药品数据
            List<Recipedetail> details = detailDAO.findByRecipeIds(recipeIds);
            List<Recipedetail> backDetails = new ArrayList<>(details.size());
            Map<Integer, Double> drugIdCountRel = Maps.newHashMap();
            Recipedetail backDetail;
            for (Recipedetail recipedetail : details) {
                Integer drugId = recipedetail.getDrugId();
                if (drugIdCountRel.containsKey(drugId)) {
                    drugIdCountRel.put(drugId, drugIdCountRel.get(recipedetail.getDrugId()) + recipedetail.getUseTotalDose());
                } else {
                    backDetail = new Recipedetail();
                    backDetail.setDrugId(recipedetail.getDrugId());
                    backDetail.setDrugName(recipedetail.getDrugName());
                    backDetail.setDrugUnit(recipedetail.getDrugUnit());
                    backDetail.setDrugSpec(recipedetail.getDrugSpec());
                    backDetail.setUseDoseUnit(recipedetail.getUseDoseUnit());
                    backDetails.add(backDetail);
                    drugIdCountRel.put(drugId, recipedetail.getUseTotalDose());
                }
            }

            //判断是否需要展示供应商详情列表，如果遇上钥世圈的药企，则都展示供应商列表
            List<DepDetailBean> depDetailList = new ArrayList<>();
            for (DrugsEnterprise dep : depList) {
                //钥世圈需要从接口获取支持药店列表
                if (DrugEnterpriseConstant.COMPANY_YSQ.equals(dep.getCallSys()) || DrugEnterpriseConstant.COMPANY_PHARMACY.equals(dep.getCallSys()) || DrugEnterpriseConstant.COMPANY_ZFB.equals(dep.getCallSys())) {
                    //需要从接口获取药店列表
                    DrugEnterpriseResult drugEnterpriseResult = remoteDrugService.findSupportDep(recipeIds, null, dep);
                    if (DrugEnterpriseResult.SUCCESS.equals(drugEnterpriseResult.getCode())) {
                        Object listObj = drugEnterpriseResult.getObject();
                        if (null != listObj && listObj instanceof List) {
                            List<DepDetailBean> ysqList = (List) listObj;
                            for (DepDetailBean d : ysqList) {
                                d.setDepId(dep.getId());
                            }
                            depDetailList.addAll(ysqList);
                        }
                        //设置样式
                        resultBean.setStyle(drugEnterpriseResult.getStyle());
                    }
                } else {
                    parseDrugsEnterprise(dep, totalMoney, depDetailList);
                    //如果是价格自定义的药企，则需要设置单独价格
                    SaleDrugListDAO saleDrugListDAO = DAOFactory.getDAO(SaleDrugListDAO.class);
                    List<Integer> drugIds = Lists.newArrayList(drugIdCountRel.keySet());
                    if (Integer.valueOf(0).equals(dep.getSettlementMode()) && (RecipeBussConstant.DEP_SUPPORT_ONLINE.equals(dep.getPayModeSupport()) || RecipeBussConstant.DEP_SUPPORT_ALL.equals(dep.getPayModeSupport()))) {
                        List<SaleDrugList> saleDrugLists = saleDrugListDAO.findByOrganIdAndDrugIds(dep.getId(), drugIds);
                        if (CollectionUtils.isNotEmpty(saleDrugLists)) {
                            BigDecimal total = BigDecimal.ZERO;
                            try {
                                for (SaleDrugList saleDrug : saleDrugLists) {
                                    //保留3位小数
                                    total = total.add(saleDrug.getPrice().multiply(new BigDecimal(drugIdCountRel.get(saleDrug.getDrugId()))).divide(BigDecimal.ONE, 3, RoundingMode.UP));
                                }
                            } catch (Exception e) {
                                LOGGER.error("findSupportDepList 重新计算药企ID为[{}]的结算价格出错. drugIds={}", dep.getId(), JSONUtils.toString(drugIds), e);
                                //此处应该要把出错的药企从返回列表中剔除
                                depDetailList.remove(depDetailList.size() - 1);
                                continue;
                            }

                            //重置药企处方价格
                            for (DepDetailBean depDetailBean : depDetailList) {
                                if (depDetailBean.getDepId().equals(dep.getId())) {
                                    depDetailBean.setRecipeFee(total);
                                    break;
                                }
                            }
                        }
                    }

                }

                //只是查询的话减少处理量
                if (0 == findDetail && depDetailList.size() > 1) {
                    depListBean.setSigle(false);
                    break;
                }
            }

            //有可能钥世圈支持配送，实际从接口处没有获取到药店
            if (CollectionUtils.isEmpty(depDetailList)) {
                resultBean.setCode(RecipeResultBean.FAIL);
                resultBean.setMsg("很抱歉，当前库存不足无法购买，请联系客服：" + cacheService.getParam(ParameterConstant.KEY_CUSTOMER_TEL, RecipeSystemConstant.CUSTOMER_TEL));
                return resultBean;
            }

            depListBean.setList(depDetailList);
            resultBean.setObject(depListBean);
            //只需要查询是否存在多个供应商， 就不需要设置其他额外信息
            if (0 == findDetail) {
                return resultBean;
            }

            if (depDetailList.size() > 1) {
                depListBean.setSigle(false);
            }

            //重置药品数量
            for (Recipedetail recipedetail : backDetails) {
                recipedetail.setUseTotalDose(drugIdCountRel.get(recipedetail.getDrugId()));
            }
            depListBean.setDetails(ObjectCopyUtils.convert(backDetails, RecipeDetailBean.class));
            //患者处方取药方式提示
            if (recipeIds.size() <= 1) {
                depListBean.setRecipeGetModeTip(RecipeServiceSub.getRecipeGetModeTip(recipeList.get(0)));
            }

        } else {
            resultBean.setCode(RecipeResultBean.FAIL);
            resultBean.setMsg("很抱歉，未能匹配到可以支持的药企，请联系客服：" + cacheService.getParam(ParameterConstant.KEY_CUSTOMER_TEL, RecipeSystemConstant.CUSTOMER_TEL));
        }

        return resultBean;
    }



    /**
     * 查询处方无库存药企药品信息
     *
     * @param recipeId
     * @return
     */
    @RpcService
    public RecipeResultBean findUnSupportDepList(Integer recipeId) {
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        RecipeService recipeService = ApplicationUtils.getRecipeService(RecipeService.class);

        RecipeResultBean resultBean = RecipeResultBean.getSuccess();
        Recipe recipe = recipeDAO.getByRecipeId(recipeId);
        if (recipe == null) {
            resultBean.setCode(RecipeResultBean.FAIL);
            resultBean.setMsg("处方不存在");
            return resultBean;
        }

        Integer organId = recipe.getClinicOrgan();
        List<DrugEnterpriseResult> unDepList = recipeService.findUnSupportDepList(recipeId, organId);
        LOGGER.info("findUnSupportDepList recipeId={}, 无库存药企信息[{}]", JSONUtils.toString(recipeId), JSONObject.toJSONString(unDepList));
        if (CollectionUtils.isNotEmpty(unDepList)) {
            resultBean.setCode(RecipeResultBean.FAIL);
            List<String> drugList = new ArrayList<>();
            for (DrugEnterpriseResult result : unDepList){
                List<String> list = (List<String>) result.getObject();
                if (CollectionUtils.isNotEmpty(list)){
                    drugList.addAll((list));
                    break;
                }
            }
            for (DrugEnterpriseResult result : unDepList){
                List<String> list = (List<String>) result.getObject();
                // 有药品名称取交集
                if (CollectionUtils.isNotEmpty(list)){
                    drugList.retainAll(list);
                }else {
                    // 有一个不能返回具体无库存药品，不展示药品名称，返回药品信息为空
                    LOGGER.info("findUnSupportDepList recipeId={}, 药企未返回具体无库存药品信息[{}]", JSONUtils.toString(recipeId), JSONObject.toJSONString(result));
                    resultBean.setObject(null);
                    break;
                }
            }
            // 仅各药企库存不足药品是包含关系才展示，即交集不为空，且交集结果至少是某一个药企无库存药品
            if (CollectionUtils.isNotEmpty(drugList)){
                Collections.sort(drugList);
                String retainStr = drugList.toString();
                Boolean showDrug = false;
                for (DrugEnterpriseResult result : unDepList){
                    List<String> list = (List<String>) result.getObject();
                    Collections.sort(list);
                    String listStr = list.toString();
                    if (retainStr.equals(listStr)){
                        showDrug = true;
                        break;
                    }
                }
                if (showDrug){
                    resultBean.setObject(drugList);
                }else {
                    resultBean.setObject(null);
                }
            }else {
                resultBean.setObject(null);
            }
        }
        LOGGER.info("findUnSupportDepList recipeId={}, 无库存药企药品信息取交集结果[{}]", JSONUtils.toString(recipeId), JSONObject.toJSONString(resultBean));
        return resultBean;
    }
    /**
     * 查询处方无库存药企药品信息
     *
     * @param recipeId
     * @return
     */
    @RpcService
    public RecipeResultBean findUnSupportDepList(Integer recipeId,List<DrugEnterpriseResult> unDepList) {
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);


        RecipeResultBean resultBean = RecipeResultBean.getSuccess();
        Recipe recipe = recipeDAO.getByRecipeId(recipeId);
        if (recipe == null) {
            resultBean.setCode(RecipeResultBean.FAIL);
            resultBean.setMsg("处方不存在");
            return resultBean;
        }

        if (CollectionUtils.isNotEmpty(unDepList)) {
            resultBean.setCode(RecipeResultBean.FAIL);
            List<String> drugList = new ArrayList<>();
            for (DrugEnterpriseResult result : unDepList){
                List<String> list = (List<String>) result.getObject();
                if (CollectionUtils.isNotEmpty(list)){
                    drugList.addAll((list));
                    break;
                }
            }
            for (DrugEnterpriseResult result : unDepList){
                List<String> list = (List<String>) result.getObject();
                // 有药品名称取交集
                if (CollectionUtils.isNotEmpty(list)){
                    drugList.retainAll(list);
                }else {
                    // 有一个不能返回具体无库存药品，不展示药品名称，返回药品信息为空
                    LOGGER.info("findUnSupportDepList recipeId={}, 药企未返回具体无库存药品信息[{}]", JSONUtils.toString(recipeId), JSONObject.toJSONString(result));
                    resultBean.setObject(null);
                    break;
                }
            }
            // 仅各药企库存不足药品是包含关系才展示，即交集不为空，且交集结果至少是某一个药企无库存药品
            if (CollectionUtils.isNotEmpty(drugList)){
                Collections.sort(drugList);
                String retainStr = drugList.toString();
                Boolean showDrug = false;
                for (DrugEnterpriseResult result : unDepList){
                    List<String> list = (List<String>) result.getObject();
                    Collections.sort(list);
                    String listStr = list.toString();
                    if (retainStr.equals(listStr)){
                        showDrug = true;
                        break;
                    }
                }
                if (showDrug){
                    resultBean.setObject(drugList);
                }else {
                    resultBean.setObject(null);
                }
            }else {
                resultBean.setObject(null);
            }
        }
        LOGGER.info("findUnSupportDepList recipeId={}, 无库存药企药品信息取交集结果[{}]", JSONUtils.toString(recipeId), JSONObject.toJSONString(resultBean));
        return resultBean;
    }

    private void parseDrugsEnterprise(DrugsEnterprise dep, BigDecimal totalMoney, List<DepDetailBean> depDetailList) {
        RecipeCacheService cacheService = ApplicationUtils.getRecipeService(RecipeCacheService.class);

        DepDetailBean depDetailBean = new DepDetailBean();
        depDetailBean.setDepId(dep.getId());
        depDetailBean.setDepName(dep.getName());
        depDetailBean.setRecipeFee(totalMoney);
        Integer supportMode = dep.getPayModeSupport();
        String giveModeText = "";
        List<Integer> payModeList = new ArrayList<>();
        //配送模式支持 0:不支持 1:线上付款 2:货到付款 3:药店取药 8:货到付款和药店取药 9:都支持 7配送到家和药店取药
        if (RecipeBussConstant.DEP_SUPPORT_ONLINE.equals(supportMode)) {
            payModeList.add(RecipeBussConstant.PAYMODE_ONLINE);
            payModeList.add(RecipeBussConstant.DEP_SUPPORT_ONLINE_TFDS);
            giveModeText = "配送到家";
            //无法配送时间文案提示
            depDetailBean.setUnSendTitle(cacheService.getParam(ParameterConstant.KEY_RECIPE_UNSEND_TIP));
        } else if (RecipeBussConstant.DEP_SUPPORT_COD.equals(supportMode)) {
            payModeList.add(RecipeBussConstant.PAYMODE_COD);
            giveModeText = "配送到家";
        } else if (RecipeBussConstant.DEP_SUPPORT_TFDS.equals(supportMode)) {
            payModeList.add(RecipeBussConstant.PAYMODE_TFDS);
            payModeList.add(RecipeBussConstant.DEP_SUPPORT_ONLINE_TFDS);
        } else if (RecipeBussConstant.DEP_SUPPORT_COD_TFDS.equals(supportMode)) {
            payModeList.add(RecipeBussConstant.PAYMODE_COD);
            payModeList.add(RecipeBussConstant.PAYMODE_TFDS);
        } else if (RecipeBussConstant.DEP_SUPPORT_ALL.equals(supportMode)) {
            payModeList.add(RecipeBussConstant.PAYMODE_ONLINE);
            payModeList.add(RecipeBussConstant.PAYMODE_COD);
            payModeList.add(RecipeBussConstant.PAYMODE_TFDS);
            payModeList.add(RecipeBussConstant.DEP_SUPPORT_ONLINE_TFDS);
            //无法配送时间文案提示
            depDetailBean.setUnSendTitle(cacheService.getParam(ParameterConstant.KEY_RECIPE_UNSEND_TIP));
        } else if (RecipeBussConstant.DEP_SUPPORT_ONLINE_TFDS.equals(supportMode)) {
            //配送到家
            payModeList.add(RecipeBussConstant.PAYMODE_ONLINE);
            //药店取药
            payModeList.add(RecipeBussConstant.PAYMODE_TFDS);
        } else if (RecipeBussConstant.DEP_SUPPORT_UNKNOW.equals(supportMode)) {
            payModeList.add(RecipeBussConstant.DEP_SUPPORT_UNKNOW);
        }
        if (CollectionUtils.isNotEmpty(payModeList)) {
            depDetailBean.setPayMode(payModeList.get(0));
        }
        depDetailBean.setGiveModeText(giveModeText);
        depDetailList.add(depDetailBean);
    }

    /**
     * 获取患者特慢病病种列表
     *
     * @return
     */
    @RpcService
    public List<ChronicDiseaseListResTO> findPatientChronicDiseaseList(Integer organId, String mpiId) {
        LOGGER.info("findPatientChronicDiseaseList organId={},mpiId={}", organId, mpiId);
        PatientService patientService = ApplicationUtils.getBasicService(PatientService.class);
        PatientDTO patientDTO = patientService.get(mpiId);
        if (patientDTO == null) {
            throw new DAOException(609, "找不到该患者");
        }
        List<ChronicDiseaseListResTO> list = Lists.newArrayList();
        IConfigurationCenterUtilsService configurationService = ApplicationUtils.getBaseService(IConfigurationCenterUtilsService.class);
        Integer diseaseType = (Integer) configurationService.getConfiguration(organId, "recipeChooseChronicDisease");
        if (3 == diseaseType) {
            List<ChronicDisease> chronicDiseaseList = chronicDiseaseDAO.findChronicDiseasesByOrganId(diseaseType.toString());
            list = ObjectCopyUtils.convert(chronicDiseaseList, ChronicDiseaseListResTO.class);
            return list;
        } else {
            RecipeToHisService service = AppContextHolder.getBean("recipeToHisService", RecipeToHisService.class);
            ChronicDiseaseListReqTO req = new ChronicDiseaseListReqTO();
            PatientBaseInfo patientBaseInfo = new PatientBaseInfo();
            patientBaseInfo.setPatientName(patientDTO.getPatientName());
            patientBaseInfo.setCertificate(patientDTO.getCertificate());
            patientBaseInfo.setCertificateType(patientDTO.getCertificateType());
            patientBaseInfo.setPatientID(patientDTO.getPatId());
            req.setPatient(patientBaseInfo);
            req.setOrganId(organId);
            HisResponseTO<PatientChronicDiseaseRes> res = service.findPatientChronicDiseaseList(req);
            if (res != null && !("200".equals(res.getMsgCode()))) {
                String msg = "接口异常";
                if (StringUtils.isNotEmpty(res.getMsg())) {
                    msg = msg + ":" + res.getMsg();
                }
                throw new DAOException(609, msg);
            }
            if (res == null || res.getData() == null) {
                return list;
            }
            list = res.getData().getChronicDiseaseListResTOs();
            HashMap<String, String> finalChronicDiseaseFlagMap = getRedisChronicDiseaseMap(organId);
            if (CollectionUtils.isNotEmpty(list) && finalChronicDiseaseFlagMap.size() > 0) {
                LOGGER.info("慢病信息转化前的list={}",JSON.toJSONString(list));
                list.forEach(
                        item -> {
                            String chronicDiseaseFlag = finalChronicDiseaseFlagMap.get(item.getChronicDiseaseFlag());
                            if(StringUtils.isNoneBlank(chronicDiseaseFlag)){
                                item.setChronicDiseaseFlag(chronicDiseaseFlag);
                            }
                        }
                );
                LOGGER.info("慢病信息转化后的list={}",JSON.toJSONString(list));
            }
            return list;
        }
    }

    /**
     * 获取患者特慢病病种列表
     *
     * @return
     */
    @RpcService
    public Map<String,Object> findPatientChronicDiseaseListNew(Integer organId, String mpiId) {
        LOGGER.info("findPatientChronicDiseaseListNew organId={},mpiId={}", organId, mpiId);
        Map<String,Object> result = Maps.newHashMap();
        PatientService patientService = ApplicationUtils.getBasicService(PatientService.class);
        PatientDTO patientDTO = patientService.get(mpiId);
        if (patientDTO == null) {
            throw new DAOException(609, "找不到该患者");
        }
        List<ChronicDiseaseListResTO> list = Lists.newArrayList();
        IConfigurationCenterUtilsService configurationService = ApplicationUtils.getBaseService(IConfigurationCenterUtilsService.class);
        //{ "id": 1, "text": "无" , "locked": true},
        //{ "id": 2, "text": "特慢病病种" },
        //{ "id": 3, "text": "重症病种" },
        //{ "id": 4, "text": "慢病病种" },
        //{ "id": 5, "text": "重医大附属二院" }
        Integer diseaseType = (Integer) configurationService.getConfiguration(organId, "recipeChooseChronicDisease");
        result.put("recipeChooseChronicDisease",diseaseType);
        if (3 == diseaseType) {
            List<ChronicDisease> chronicDiseaseList = chronicDiseaseDAO.findChronicDiseasesByOrganId(diseaseType.toString());
            list = ObjectCopyUtils.convert(chronicDiseaseList, ChronicDiseaseListResTO.class);
        } else {
            if (1 == diseaseType){
                result.put("chronicDiseaseList",list);
                return result;
            }
            RecipeToHisService service = AppContextHolder.getBean("recipeToHisService", RecipeToHisService.class);
            ChronicDiseaseListReqTO req = new ChronicDiseaseListReqTO();
            PatientBaseInfo patientBaseInfo = new PatientBaseInfo();
            patientBaseInfo.setPatientName(patientDTO.getPatientName());
            patientBaseInfo.setCertificate(patientDTO.getCertificate());
            patientBaseInfo.setCertificateType(patientDTO.getCertificateType());
            patientBaseInfo.setPatientID(patientDTO.getPatId());
            req.setPatient(patientBaseInfo);
            req.setOrganId(organId);
            HisResponseTO<PatientChronicDiseaseRes> res = service.findPatientChronicDiseaseList(req);
            if (res != null && !("200".equals(res.getMsgCode()))) {
                String msg = "接口异常";
                if (StringUtils.isNotEmpty(res.getMsg())) {
                    msg = msg + ":" + res.getMsg();
                }
                throw new DAOException(609, msg);
            }
            if (res != null && res.getData() != null) {
                list = res.getData().getChronicDiseaseListResTOs();
                HashMap<String, String> finalChronicDiseaseFlagMap = getRedisChronicDiseaseMap(organId);
                if (CollectionUtils.isNotEmpty(list) && finalChronicDiseaseFlagMap.size() > 0) {
                    LOGGER.info("慢病信息转化前的list={}",JSON.toJSONString(list));
                    list.forEach(
                            item -> {
                                String chronicDiseaseFlag = finalChronicDiseaseFlagMap.get(item.getChronicDiseaseFlag());
                                if(StringUtils.isNoneBlank(chronicDiseaseFlag)){
                                    item.setChronicDiseaseFlag(chronicDiseaseFlag);
                                }
                            }
                    );
                    LOGGER.info("慢病信息转化后的list={}",JSON.toJSONString(list));
                }
                try {
                    if (CollectionUtils.isNotEmpty(list)&& (5 == diseaseType)){
                        //第一层
                        List<RankShiftList> rankShiftList = Lists.newArrayList();
                        Map<String, List<ChronicDiseaseListResTO>> flagMap = list.stream().collect(Collectors.groupingBy(ChronicDiseaseListResTO::getChronicDiseaseFlag));
                        Map<String, String> codeNameMap = list.stream().collect(Collectors.toMap(ChronicDiseaseListResTO::getChronicDiseaseCode, ChronicDiseaseListResTO::getChronicDiseaseName, (k1, k2) -> k1));
                        flagMap.forEach((k,v)->{
                            RankShiftList rank = new RankShiftList();
                            rank.setChronicDiseaseFlag(k);
                            try {
                                rank.setChronicDiseaseFlagText(DictionaryController.instance().get("eh.cdr.dictionary.ChronicDiseaseFlag").getText(k));
                            } catch (ControllerException e) {
                                LOGGER.error("findPatientChronicDiseaseListNew error",e);
                            }
                            //第二层
                            Map<String, List<ChronicDiseaseListResTO>> codeMap = v.stream().collect(Collectors.groupingBy(ChronicDiseaseListResTO::getChronicDiseaseCode));
                            List<RankShiftList> rankShiftList1 = Lists.newArrayList();
                            codeMap.forEach((k1,k2)->{
                                RankShiftList rank1 = new RankShiftList();
                                rank1.setChronicDiseaseCode(k1);
                                rank1.setChronicDiseaseName(codeNameMap.get(k1));
                                //第三层
                                List<RankShiftList> rankShiftList2 = Lists.newArrayList();
                                for (ChronicDiseaseListResTO resTO:k2){
                                    if (StringUtils.isNotEmpty(resTO.getComplication())){
                                        RankShiftList rankShift2 = new RankShiftList();
                                        rankShift2.setComplication(resTO.getComplication());
                                        rankShiftList2.add(rankShift2);
                                    }
                                }
                                rank1.setRankShiftList(rankShiftList2);
                                rankShiftList1.add(rank1);
                            });
                            rank.setRankShiftList(rankShiftList1);
                            rankShiftList.add(rank);
                        });
                        result.put("rankShiftList",rankShiftList);
                    }
                } catch (Exception e) {
                    LOGGER.error("findPatientChronicDiseaseListNew error",e);
                }
                result.put("medicalType",res.getData().getPatientType());
                result.put("medicalTypeText",res.getData().getPatientTypeText());
            }
        }
        result.put("chronicDiseaseList",list);
        return result;
    }

    /**
     * 查询线下患者信息接口
     *
     * @return
     */
    @RpcService
    public PatientQueryRequestTO queryPatientForHis(Integer organId, String mpiId) {
        LOGGER.info("queryPatientForHis organId={},mpiId={}", organId, mpiId);
        return queryPatientForHisV1(organId, mpiId, null);
    }

    /**
     * 查询线下患者信息接口
     *
     * @return
     */
    @RpcService
    public PatientQueryRequestTO queryPatientForHisV1(Integer organId, String mpiId, Integer clinicId) {
        LOGGER.info("queryPatientForHisV1 organId={},mpiId={}", organId, mpiId);

        PatientDTO patient = patientClient.getPatientBeanByMpiId(mpiId);
        if (patient == null) {
            throw new DAOException(609, "找不到该患者");
        }
        try {
            PatientQueryRequestTO req = new PatientQueryRequestTO();
            req.setOrgan(organId);
            req.setPatientName(patient.getPatientName());
            req.setCertificateType(patient.getCertificateType());
            req.setCertificate(patient.getCertificate());
            if (clinicId != null) {
                RevisitExDTO consultExDTO = revisitClient.getByClinicId(clinicId);
                if (consultExDTO != null) {
                    req.setPatientID(consultExDTO.getPatId());
                }
            }
            LOGGER.info("queryPatientForHisV1 req={}", JSONUtils.toString(req));
            PatientQueryRequestTO patientQueryRequestTO = patientClient.queryPatient(req);
            LOGGER.info("queryPatientForHisV1 patientQueryRequestTO={}", JSONUtils.toString(patientQueryRequestTO));
            if (null == patientQueryRequestTO || CollectionUtils.isEmpty(patientQueryRequestTO.getHisCards())) {
                return null;
            }
            List<HisCardVO> hisCardVOS = patientQueryRequestTO.getHisCards();
            //默认获取第一张卡类型
            if ("2".equals(hisCardVOS.get(0).getCardType())){
                patientQueryRequestTO.setMedicalType("2");
                patientQueryRequestTO.setMedicalTypeText("医保");
            } else {
                patientQueryRequestTO.setMedicalType("1");
                patientQueryRequestTO.setMedicalTypeText("自费");
            }
            LOGGER.info("queryPatientForHisV1 patientQueryRequestTO:{}.", JSONUtils.toString(patientQueryRequestTO));
            return patientQueryRequestTO;
        } catch (Exception e) {
            LOGGER.error("queryPatientForHisV1 error", e);
            throw new DAOException(609, "查患者信息异常:"+e.getMessage());
        }
    }

    /**
     * 校验当前就诊人是否有效 是否实名认证 就诊卡是否有效
     * @param outPatientReqVO 当前就诊人信息
     * @return 枚举值
     */
    @Override
    public Integer checkCurrentPatient(OutPatientReqVO outPatientReqVO){
        LOGGER.info("OutPatientRecipeService checkCurrentPatient outPatientReqVO:{}.", JSON.toJSONString(outPatientReqVO));
        PatientDTO patientDTO = patientClient.getPatientBeanByMpiId(outPatientReqVO.getMpiId());
        if (null == patientDTO || !new Integer(1).equals(patientDTO.getStatus())) {
            return CheckPatientEnum.CHECK_PATIENT_PATIENT.getType();
        }
        if (!new Integer(1).equals(patientDTO.getAuthStatus())) {
            return CheckPatientEnum.CHECK_PATIENT_NOAUTH.getType();
        }
        Collection result = patientClient.findHealthCard(outPatientReqVO.getMpiId());
        if (null == result || (StringUtils.isNotEmpty(outPatientReqVO.getCardID()) && !result.contains(outPatientReqVO.getCardID()))) {
            return CheckPatientEnum.CHECK_PATIENT_CARDDEL.getType();
        }
        return CheckPatientEnum.CHECK_PATIENT_NORMAL.getType();
    }

    /**
     * 根据mpiId获取患者信息
     * @param mpiId 患者唯一号
     * @return 患者信息
     */
    @Override
    public PatientDTO getPatientDTOByMpiID(String mpiId){
        return patientClient.getPatientBeanByMpiId(mpiId);
    }

    /**
     * 获取患者医保信息
     * @param patientInfoVO 患者信息
     * @return 医保类型相关
     */
    @Override
    public PatientMedicalTypeVO queryPatientMedicalType(PatientInfoVO patientInfoVO) {
        LOGGER.info("OutPatientRecipeService queryPatientMedicalType patientInfoVO:{}.", JSON.toJSONString(patientInfoVO));
        PatientMedicalTypeVO patientMedicalTypeVO = new PatientMedicalTypeVO("1", "自费");
        if (ValidateUtil.integerIsEmpty(patientInfoVO.getClinicId())){
            return patientMedicalTypeVO;
        }
        RevisitExDTO revisitExDTO = revisitClient.getByClinicId(patientInfoVO.getClinicId());
        if (null == revisitExDTO) {
            return patientMedicalTypeVO;
        }
        if (null != revisitExDTO.getMedicalFlag() && MedicalTypeEnum.MEDICAL_PAY.getType().equals(revisitExDTO.getMedicalFlag())) {
            return new PatientMedicalTypeVO("2", "医保");
        } else {
            return patientMedicalTypeVO;
        }
    }

    @Override
    public List<FormWorkRecipeVO> findFormWorkRecipe(FormWorkRecipeReqVO formWorkRecipeReqVO) {
        String formWorkRecipe = recipeParameterDao.getByName(formWorkRecipeReqVO.getOrganId()+"_formWorkRecipe");
        //解析json数据
        List<FormWorkRecipeVO> workRecipeVOList = JSON.parseArray(formWorkRecipe, FormWorkRecipeVO.class);
        LOGGER.info("findFormWorkRecipe workRecipeVOList:{}", JSONUtils.toString(workRecipeVOList));
        return workRecipeVOList;
    }

    @Override
    public Integer saveRecipe(RecipeInfoVO recipeInfoVO) {
        //保存处方
        Recipe recipe = ObjectCopyUtils.convert(recipeInfoVO.getRecipeBean(), Recipe.class);
        recipe = recipeManager.saveRecipe(recipe);
        //保存处方扩展
        if (null != recipeInfoVO.getRecipeExtendBean()) {
            RecipeExtend recipeExtend = ObjectCopyUtils.convert(recipeInfoVO.getRecipeExtendBean(), RecipeExtend.class);
            String cardNo = recipeManager.getCardNoByRecipe(recipe);
            if (StringUtils.isNotEmpty(cardNo)) {
                recipeExtend.setCardNo(cardNo);
            }
            recipeManager.setRecipeInfoFromRevisit(recipe, recipeExtend);
            recipeManager.setRecipeChecker(recipe);
            //设置购药方式
            this.setRecipeSupportGiveMode(recipe);
            recipeManager.saveRecipeExtend(recipeExtend, recipe);
        }
        //保存处方明细
        if (CollectionUtils.isNotEmpty(recipeInfoVO.getRecipeDetails())) {
            List<Recipedetail> details = ObjectCopyUtils.convert(recipeInfoVO.getRecipeDetails(), Recipedetail.class);
            List<Integer> drugIds = details.stream().map(Recipedetail::getDrugId).collect(Collectors.toList());
            Map<String, OrganDrugList> organDrugListMap = organDrugListManager.getOrganDrugByIdAndCode(recipe.getClinicOrgan(), drugIds);
            recipeDetailManager.saveRecipeDetails(recipe, details, organDrugListMap);
        }
        recipe = recipeManager.saveRecipe(recipe);
        //将处方写入HIS
        offlineRecipeBusinessService.pushRecipe(recipe.getRecipeId(), CommonConstant.RECIPE_PUSH_TYPE, CommonConstant.RECIPE_PATIENT_TYPE, null, null);
        return recipe.getRecipeId();
    }

    @Override
    public Integer esignRecipeCa(Integer recipeId) {
        try {
            Recipe recipe = recipeManager.getRecipeById(recipeId);
            createPdfFactory.queryPdfOssId(recipe);
            createPdfFactory.updateCheckNamePdfESign(recipeId);
        } catch (Exception e) {
            LOGGER.error("esignRecipeCa error", e);
        }
        return null;
    }

    /**
     * @desc 做一个疾病类型的适配
     * @author 毛泽
     * @param organId 
     * @return
     */
    private HashMap<String,String> getRedisChronicDiseaseMap(Integer organId){
        HashMap<String,String> chronicDiseaseFlagMap = new HashMap<>();
        try {
            RedisClient redisClient = AppContextHolder.getBean("redisClient", RedisClient.class);
            String ChronicDiseaseFlagStr = redisClient.get(CacheConstant.KEY_CHRONIC_DISEASE_FLAG+organId);
            if(StringUtils.isNoneBlank(ChronicDiseaseFlagStr)) {
                    chronicDiseaseFlagMap = JSON.parseObject(ChronicDiseaseFlagStr,HashMap.class);

            }
        } catch (Exception e) {
            LOGGER.error("getRedisChronicDiseaseMap error",e);
        }
        LOGGER.info("getRedisChronicDiseaseMap={}",JSON.toJSONString(chronicDiseaseFlagMap));
        return  chronicDiseaseFlagMap;
    }

    private void setRecipeSupportGiveMode(Recipe recipe){
        //从运营平台获取配置项
        GiveModeShowButtonDTO giveModeShowButtonDTO = operationClient.getGiveModeSettingFromYypt(recipe.getClinicOrgan());
        if (CollectionUtils.isEmpty(giveModeShowButtonDTO.getGiveModeButtons())) {
            return;
        }
        List<GiveModeButtonDTO> giveModeButtonDTOList = giveModeShowButtonDTO.getGiveModeButtons();
        StringBuilder recipeSupportGiveMode = new StringBuilder();
        giveModeButtonDTOList.forEach(giveModeButtonDTO -> {
            Integer giveMode = RecipeSupportGiveModeEnum.getGiveMode(giveModeButtonDTO.getShowButtonKey());
            if (!new Integer(0).equals(giveMode) && !recipeSupportGiveMode.toString().contains(giveMode.toString())) {
                recipeSupportGiveMode.append(giveMode).append(",");
            }
        });
        LOGGER.info("setRecipeSupportGiveMode recipeSupportGiveMode:{}", recipeSupportGiveMode.toString());
        recipeSupportGiveMode.deleteCharAt(recipeSupportGiveMode.lastIndexOf(","));
        LOGGER.info("setRecipeSupportGiveMode recipeSupportGiveMode last:{}", recipeSupportGiveMode.toString());
        recipe.setRecipeSupportGiveMode(recipeSupportGiveMode.toString());
    }
}
