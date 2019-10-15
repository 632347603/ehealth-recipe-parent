package recipe.service;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.ngari.base.BaseAPI;
import com.ngari.base.employment.service.IEmploymentService;
import com.ngari.base.hisconfig.service.IHisConfigService;
import com.ngari.base.patient.model.HealthCardBean;
import com.ngari.base.patient.model.PatientBean;
import com.ngari.base.patient.service.IPatientService;
import com.ngari.bus.hosrelation.model.HosrelationBean;
import com.ngari.bus.hosrelation.service.IHosrelationService;
import com.ngari.base.property.service.IConfigurationCenterUtilsService;
import com.ngari.common.mode.HisResponseTO;
import com.ngari.his.recipe.mode.*;
import com.ngari.patient.dto.DepartmentDTO;
import com.ngari.patient.service.BasicAPI;
import com.ngari.patient.service.DepartmentService;
import com.ngari.patient.service.OrganService;
import com.ngari.patient.utils.ObjectCopyUtils;
import com.ngari.recipe.common.RecipeResultBean;
import com.ngari.recipe.entity.DrugList;
import com.ngari.recipe.entity.OrganDrugList;
import com.ngari.recipe.entity.Recipe;
import com.ngari.recipe.entity.Recipedetail;
import com.ngari.recipe.recipe.model.RecipeBean;
import ctd.persistence.DAOFactory;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcBean;
import ctd.util.annotation.RpcService;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import recipe.ApplicationUtils;
import recipe.bean.CheckYsInfoBean;
import recipe.bean.RecipeCheckPassResult;
import recipe.bussutil.RecipeUtil;
import recipe.bussutil.UsePathwaysFilter;
import recipe.bussutil.UsingRateFilter;
import recipe.constant.BusTypeEnum;
import recipe.constant.CacheConstant;
import recipe.constant.RecipeBussConstant;
import recipe.constant.RecipeStatusConstant;
import recipe.dao.DrugListDAO;
import recipe.dao.OrganDrugListDAO;
import recipe.dao.RecipeDAO;
import recipe.dao.RecipeDetailDAO;
import recipe.dao.bean.DrugInfoHisBean;
import recipe.hisservice.HisRequestInit;
import recipe.hisservice.RecipeToHisService;
import recipe.util.DateConversion;
import recipe.util.RedisClient;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author yu_yun
 * his接口服务
 */
@RpcBean("recipeHisService")
public class RecipeHisService extends RecipeBaseService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RecipeHisService.class);

    private IPatientService iPatientService = ApplicationUtils.getBaseService(IPatientService.class);

    @Autowired
    private RedisClient redisClient;

    /**
     * 发送处方
     *
     * @param recipeId
     */
    @RpcService
    public boolean recipeSendHis(Integer recipeId, Integer otherOrganId) {
        boolean result = true;
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);

        Recipe recipe = recipeDAO.getByRecipeId(recipeId);
        if (null == recipe) {
            return false;
        }
        //中药处方由于不需要跟HIS交互，故读写分离后有可能查询不到数据
        if (skipHis(recipe)) {
            RecipeCheckPassResult recipeCheckPassResult = new RecipeCheckPassResult();
            recipeCheckPassResult.setRecipeId(recipeId);
            recipeCheckPassResult.setRecipeCode(RandomStringUtils.randomAlphanumeric(10));
            HisCallBackService.checkPassSuccess(recipeCheckPassResult, true);
            return result;
        }

        Integer sendOrganId = (null == otherOrganId) ? recipe.getClinicOrgan() : otherOrganId;
        if (isHisEnable(sendOrganId)) {
            RecipeDetailDAO recipeDetailDAO = DAOFactory.getDAO(RecipeDetailDAO.class);
            OrganDrugListDAO drugDao = DAOFactory.getDAO(OrganDrugListDAO.class);
            RecipeToHisService service = AppContextHolder.getBean("recipeToHisService", RecipeToHisService.class);
            IEmploymentService iEmploymentService = ApplicationUtils.getBaseService(IEmploymentService.class);

            List<Recipedetail> details = recipeDetailDAO.findByRecipeId(recipeId);
            PatientBean patientBean = iPatientService.get(recipe.getMpiid());
            HealthCardBean cardBean = iPatientService.getHealthCard(recipe.getMpiid(), recipe.getClinicOrgan(), "2");
            //创建请求体
            RecipeSendRequestTO request = HisRequestInit.initRecipeSendRequestTO(recipe, details, patientBean, cardBean);
            //是否是武昌机构，替换请求体
            Set<String> organIdList = redisClient.sMembers(CacheConstant.KEY_WUCHANG_ORGAN_LIST);
            if (CollectionUtils.isNotEmpty(organIdList) && organIdList.contains(sendOrganId.toString())) {
                request = HisRequestInit.initRecipeSendRequestTOForWuChang(recipe, details, patientBean, cardBean);
                //发送电子病历
                DocIndexToHisReqTO docIndexToHisReqTO = HisRequestInit.initDocIndexToHisReqTO(recipe);
                HisResponseTO<DocIndexToHisResTO> hisResponseTO = service.docIndexToHis(docIndexToHisReqTO);
                if (hisResponseTO != null){
                    if ("200".equals(hisResponseTO.getMsgCode())){
                        //电子病历接口返回挂号序号
                        if (hisResponseTO.getData()!=null){
                            request.setRegisteredId(hisResponseTO.getData().getRegisterId());
                            request.setRegisterNo(hisResponseTO.getData().getRegisterNo());
                            request.setPatientId(hisResponseTO.getData().getPatientId());
                        }
                        RecipeLogService.saveRecipeLog(recipe.getRecipeId(), recipe.getStatus(), recipe.getStatus(), "推送电子病历成功");
                    }else {
                        RecipeLogService.saveRecipeLog(recipe.getRecipeId(), recipe.getStatus(), recipe.getStatus(), "推送电子病历失败。原因："+hisResponseTO.getMsg());
                    }
                }
            }
            //设置医生工号
            request.setDoctorID(iEmploymentService.getJobNumberByDoctorIdAndOrganIdAndDepartment(recipe.getDoctor(), sendOrganId, recipe.getDepart()));
            //查询生产厂家
            List<OrderItemTO> orderItemList = request.getOrderList();
            if (CollectionUtils.isNotEmpty(orderItemList)) {
                List<Integer> drugIdList = FluentIterable.from(orderItemList).transform(new Function<OrderItemTO, Integer>() {
                    @Override
                    public Integer apply(OrderItemTO input) {
                        return input.getDrugId();
                    }
                }).toList();

                List<OrganDrugList> organDrugList = drugDao.findByOrganIdAndDrugIds(sendOrganId, drugIdList);
                Map<String, OrganDrugList> drugIdAndProduce = Maps.uniqueIndex(organDrugList, new Function<OrganDrugList, String>() {
                    @Override
                    public String apply(OrganDrugList input) {
                        return input.getOrganDrugCode();
                    }
                });

                OrganDrugList organDrug;
                for (OrderItemTO item : orderItemList) {
                    organDrug = drugIdAndProduce.get(item.getDrcode());
                    if (null != organDrug) {
                        item.setManfcode(organDrug.getProducerCode());
                    }
                }

            }
            request.setOrganID(sendOrganId.toString());
            // 处方独立出来后,his根据域名来判断回调模块
            service.recipeSend(request);
        } else {
            result = false;
            LOGGER.error("recipeSendHis 医院HIS未启用[organId:" + sendOrganId + ",recipeId:" + recipeId + "]");
        }
        return result;
    }

    /**
     * 更新处方状态推送his服务
     *
     * @param recipeId
     */
    @RpcService
    public boolean recipeStatusUpdate(Integer recipeId) {
        return recipeStatusUpdateWithOrganId(recipeId, null, null);
    }

    /**
     * 发送指定HIS修改处方状态
     *
     * @param recipeId
     * @param otherOrganId
     * @return
     */
    @RpcService
    public boolean recipeStatusUpdateWithOrganId(Integer recipeId, Integer otherOrganId, String hisRecipeStatus) {
        boolean flag = true;
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);

        Recipe recipe = recipeDAO.getByRecipeId(recipeId);
        if (null == recipe) {
            return false;
        }
        if (skipHis(recipe)) {
            return flag;
        }

        Integer sendOrganId = (null == otherOrganId) ? recipe.getClinicOrgan() : otherOrganId;
        if (isHisEnable(sendOrganId)) {
            RecipeDetailDAO recipeDetailDAO = DAOFactory.getDAO(RecipeDetailDAO.class);
            RecipeToHisService service = AppContextHolder.getBean("recipeToHisService", RecipeToHisService.class);

            List<Recipedetail> details = recipeDetailDAO.findByRecipeId(recipeId);
            PatientBean patientBean = iPatientService.get(recipe.getMpiid());
            HealthCardBean cardBean = iPatientService.getHealthCard(recipe.getMpiid(), recipe.getClinicOrgan(), "2");
            RecipeStatusUpdateReqTO request = HisRequestInit.initRecipeStatusUpdateReqTO(recipe, details, patientBean, cardBean);
            //是否是武昌机构，替换请求体
            Set<String> organIdList = redisClient.sMembers(CacheConstant.KEY_WUCHANG_ORGAN_LIST);
            if (CollectionUtils.isNotEmpty(organIdList) && organIdList.contains(sendOrganId.toString())) {
                request = HisRequestInit.initRecipeStatusUpdateReqForWuChang(recipe, details, patientBean, cardBean);
            }
            request.setOrganID(sendOrganId.toString());
            if (StringUtils.isNotEmpty(hisRecipeStatus)) {
                request.setRecipeStatus(hisRecipeStatus);
            }

            flag = service.recipeUpdate(request);
        } else {
            flag = false;
            LOGGER.error("recipeStatusUpdate 医院HIS未启用[organId:" + sendOrganId + ",recipeId:" + recipeId + "]");
        }

        return flag;
    }

    /**
     * 处方退款推送his服务
     *
     * @param recipeId
     */
    @RpcService
    public String recipeRefund(Integer recipeId) {
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);

        Recipe recipe = recipeDAO.getByRecipeId(recipeId);
        if (null == recipe) {
            return "处方不存在";
        }
        String backInfo = "成功";
        if (skipHis(recipe)) {
            return backInfo;
        }
        if (isHisEnable(recipe.getClinicOrgan())) {
            RecipeDetailDAO recipeDetailDAO = DAOFactory.getDAO(RecipeDetailDAO.class);
            RecipeToHisService service = AppContextHolder.getBean("recipeToHisService", RecipeToHisService.class);

            List<Recipedetail> details = recipeDetailDAO.findByRecipeId(recipeId);
            PatientBean patientBean = iPatientService.get(recipe.getMpiid());
            HealthCardBean cardBean = iPatientService.getHealthCard(recipe.getMpiid(), recipe.getClinicOrgan(), "2");
            RecipeRefundReqTO request = HisRequestInit.initRecipeRefundReqTO(recipe, details, patientBean, cardBean);

            RecipeRefundResTO response = service.recipeRefund(request);
            if (null == response || null == response.getMsgCode()) {
                backInfo = "response is null";
            } else {
                if (0 != response.getMsgCode()) {
                    backInfo = response.getMsg();
                }
            }
            RecipeLogService.saveRecipeLog(recipe.getRecipeId(), recipe.getStatus(), recipe.getStatus(), "同步HIS退款返回：" + backInfo);
        } else {
            RecipeLogService.saveRecipeLog(recipe.getRecipeId(), recipe.getStatus(), recipe.getStatus(), "recipeRefund[RecipeRefundService] HIS未启用");
            LOGGER.error("recipeRefund 医院HIS未启用[organId:" + recipe.getClinicOrgan() + ",recipeId:" + recipe.getRecipeId() + "]");
        }

        return backInfo;
    }

    /**
     * 处方购药方式及支付状态修改
     *
     * @param recipeId
     * @param payFlag
     * @param result
     */
    @RpcService
    public RecipeResultBean recipeDrugTake(Integer recipeId, Integer payFlag, RecipeResultBean result) {
        if (null == result) {
            result = RecipeResultBean.getSuccess();
        }
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);

        Recipe recipe = recipeDAO.getByRecipeId(recipeId);
        if (null == recipe) {
            result.setCode(RecipeResultBean.FAIL);
            result.setError("处方不存在");
            return result;
        }
        if (skipHis(recipe)) {
            return result;
        }

        Integer status = recipe.getStatus();
        if (isHisEnable(recipe.getClinicOrgan())) {
            RecipeDetailDAO recipeDetailDAO = DAOFactory.getDAO(RecipeDetailDAO.class);
            RecipeToHisService service = AppContextHolder.getBean("recipeToHisService", RecipeToHisService.class);

            List<Recipedetail> details = recipeDetailDAO.findByRecipeId(recipeId);
            PatientBean patientBean = iPatientService.get(recipe.getMpiid());
            HealthCardBean cardBean = iPatientService.getHealthCard(recipe.getMpiid(), recipe.getClinicOrgan(), "2");
            DrugTakeChangeReqTO request = HisRequestInit.initDrugTakeChangeReqTO(recipe, details, patientBean, cardBean);

            Boolean success = service.drugTakeChange(request);
            if (success) {
                RecipeLogService.saveRecipeLog(recipe.getRecipeId(), status, status, "HIS更新购药方式返回：写入his成功");
            } else {
                RecipeLogService.saveRecipeLog(recipe.getRecipeId(), status, status, "HIS更新购药方式返回：写入his失败");
                if (!RecipeBussConstant.GIVEMODE_TO_HOS.equals(recipe.getGiveMode())) {
                    LOGGER.error("HIS drugTake synchronize error. recipeId=" + recipeId);
                    //配送到家同步失败则返回异常,医院取药不需要管，医院处方默认是医院取药
//                        HisCallBackService.havePayFail(_dbRecipe.getRecipeId());
                    result.setCode(RecipeResultBean.FAIL);
                    result.setError("由于医院接口异常，购药方式修改失败。");
                }
            }

            //线上支付完成需要发送消息
            if (RecipeResultBean.SUCCESS.equals(result.getCode()) && RecipeBussConstant.PAYMODE_ONLINE.equals(recipe.getPayMode()) && 1 == payFlag) {
                PayNotifyReqTO payNotifyReq = HisRequestInit.initPayNotifyReqTO(recipe, patientBean, cardBean);
                Recipedetail recipedetail = service.payNotify(payNotifyReq);
                if (null != recipedetail) {
                    HisCallBackService.havePaySuccess(recipe.getRecipeId(), recipedetail);
                } else {
                    HisCallBackService.havePayFail(recipe.getRecipeId());
                    result.setCode(RecipeResultBean.FAIL);
                    result.setError("由于医院接口异常，支付失败，建议您稍后重新支付。");
                }
            }
        } else {
            RecipeLogService.saveRecipeLog(recipe.getRecipeId(), status, status, "recipeDrugTake[DrugTakeUpdateService] HIS未启用");
            LOGGER.error("recipeDrugTake 医院HIS未启用[organId:" + recipe.getClinicOrgan() + ",recipeId:" + recipe.getRecipeId() + "]");
            result.setCode(RecipeResultBean.FAIL);
            result.setError("医院HIS未启用。");
        }

        return result;
    }

    /**
     * 处方批量查询
     *
     * @param recipeCodes
     * @param organId
     */
    @RpcService
    public void recipeListQuery(List<String> recipeCodes, Integer organId) {
        if (isHisEnable(organId)) {
            RecipeToHisService service = AppContextHolder.getBean("recipeToHisService", RecipeToHisService.class);
            RecipeListQueryReqTO request = new RecipeListQueryReqTO(recipeCodes, organId);
            service.listQuery(request);
        } else {
            LOGGER.error("recipeListQuery 医院HIS未启用[organId:" + organId + ",recipeIds:" + JSONUtils.toString(recipeCodes) + "]");
        }
    }

    /**
     * 处方完成
     *
     * @param recipeId
     */
    @RpcService
    public boolean recipeFinish(Integer recipeId) {
        boolean result = true;
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);

        Recipe recipe = recipeDAO.getByRecipeId(recipeId);
        if (null == recipe) {
            return false;
        }
        if (skipHis(recipe)) {
            return result;
        }

        if (isHisEnable(recipe.getClinicOrgan())) {
            RecipeDetailDAO recipeDetailDAO = DAOFactory.getDAO(RecipeDetailDAO.class);
            RecipeToHisService service = AppContextHolder.getBean("recipeToHisService", RecipeToHisService.class);

            List<Recipedetail> details = recipeDetailDAO.findByRecipeId(recipeId);
            PatientBean patientBean = iPatientService.get(recipe.getMpiid());
            HealthCardBean cardBean = iPatientService.getHealthCard(recipe.getMpiid(), recipe.getClinicOrgan(), "2");
            RecipeStatusUpdateReqTO request = HisRequestInit.initRecipeStatusUpdateReqTO(recipe, details, patientBean, cardBean);

            String memo = "";
            if (RecipeBussConstant.GIVEMODE_SEND_TO_HOME.equals(recipe.getGiveMode())) {
                memo = "配送到家完成";
            } else if (RecipeBussConstant.GIVEMODE_TFDS.equals(recipe.getGiveMode())) {
                memo = "到店取药完成";
            } else{
                memo = "患者取药完成";
            }
            boolean sendToHisFlag = service.recipeUpdate(request);
            if (sendToHisFlag) {
                //日志记录
                RecipeLogService.saveRecipeLog(recipeId, RecipeStatusConstant.FINISH, RecipeStatusConstant.FINISH, memo + "：写入his成功");
            } else {
                result = false;
                //日志记录
                RecipeLogService.saveRecipeLog(recipeId, RecipeStatusConstant.FINISH, RecipeStatusConstant.FINISH, memo + "：写入his失败");
            }
        } else {
            result = false;
            RecipeLogService.saveRecipeLog(recipeId, RecipeStatusConstant.FINISH, RecipeStatusConstant.FINISH, "recipeFinish[RecipeStatusUpdateService] HIS未启用");
            LOGGER.error("recipeFinish 医院HIS未启用[organId:" + recipe.getClinicOrgan() + ",recipeId:" + recipeId + "]");
        }

        return result;
    }

    /**
     * 单个处方查询
     *
     * @param recipeId
     * @return
     */
    @RpcService
    public String recipeSingleQuery(Integer recipeId) {
        String backInfo = "";
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);

        Recipe recipe = recipeDAO.getByRecipeId(recipeId);
        if (null == recipe) {
            return "处方不存在";
        }
        if (skipHis(recipe)) {
            return backInfo;
        }

        if (isHisEnable(recipe.getClinicOrgan())) {
            RecipeToHisService service = AppContextHolder.getBean("recipeToHisService", RecipeToHisService.class);
            RecipeListQueryReqTO request = new RecipeListQueryReqTO(recipe.getRecipeCode(), recipe.getClinicOrgan());
            Integer status = service.listSingleQuery(request);
            //审核通过的处方才能点击
            if (!Integer.valueOf(RecipeStatusConstant.CHECK_PASS).equals(status)) {
                LOGGER.error("recipeSingleQuery recipeId=" + recipeId + " not check pass status!");
                if (null == status) {
                    backInfo = "医院接口异常，请稍后再试！";
                } else {
                    backInfo = "处方单已处理！";
                }
            }
        } else {
            LOGGER.error("recipeSingleQuery 医院HIS未启用[organId:" + recipe.getClinicOrgan() + ",recipeId:" + recipeId + "]");
            backInfo = "医院系统维护中！";

        }

        return backInfo;
    }

    /**
     * 从医院HIS获取药品信息
     *
     * @param organId
     * @param searchAll true:查询该医院所有有效药品信息， false:查询限定范围内无效药品信息
     * @return
     */
    @RpcService
    public List<DrugInfoTO> getDrugInfoFromHis(int organId, boolean searchAll, int start) {
        if (isHisEnable(organId)) {
            RecipeToHisService service = AppContextHolder.getBean("recipeToHisService", RecipeToHisService.class);
            OrganDrugListDAO organDrugListDAO = DAOFactory.getDAO(OrganDrugListDAO.class);

            List<DrugInfoTO> requestList = null;
            List<DrugInfoTO> backList = null;
            if (searchAll) {
                backList = service.queryDrugInfo(requestList, organId);
            } else {
                List<DrugInfoHisBean> drugInfoList = organDrugListDAO.findDrugInfoByOrganId(organId, start, 100);
                if (CollectionUtils.isNotEmpty(drugInfoList)) {
                    requestList = Lists.newArrayList();
                    DrugInfoTO drugInfoTO;
                    for(DrugInfoHisBean drugInfoHisBean : drugInfoList){
                        drugInfoTO = new DrugInfoTO();
                        BeanUtils.copyProperties(drugInfoHisBean, drugInfoTO);
                        requestList.add(drugInfoTO);
                    }
                    backList = service.queryDrugInfo(requestList, organId);
                }
            }

            return backList;
        } else {
            LOGGER.error("getDrugInfoFromHis 医院HIS未启用[organId:" + organId + "]");
        }

        return null;
    }

    @RpcService
    public RecipeResultBean scanDrugStockByRecipeId(Integer recipeId) {
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        RecipeDetailDAO recipedetailDAO = DAOFactory.getDAO(RecipeDetailDAO.class);

        Recipe recipe = recipeDAO.getByRecipeId(recipeId);
        List<Recipedetail> detailList = recipedetailDAO.findByRecipeId(recipeId);
        if (Integer.valueOf(1).equals(recipe.getTakeMedicine())) {
            //外带药处方则不进行校验
            return RecipeResultBean.getSuccess();
        }
        return this.scanDrugStock(recipe, detailList);
    }


    /**
     * 检查医院库存
     *
     * @return
     */
    @RpcService
    public RecipeResultBean scanDrugStock(Recipe recipe, List<Recipedetail> detailList) {
        RecipeResultBean result = RecipeResultBean.getSuccess();
        OrganDrugListDAO organDrugListDAO = DAOFactory.getDAO(OrganDrugListDAO.class);

        if (null == recipe) {
            result.setCode(RecipeResultBean.FAIL);
            result.setError("没有该处方");
            return result;
        }

        if (skipHis(recipe)) {
            return result;
        }

        if (CollectionUtils.isEmpty(detailList)) {
            result.setCode(RecipeResultBean.FAIL);
            result.setError("处方没有详情");
            return result;
        }

        if (isHisEnable(recipe.getClinicOrgan())) {
            RecipeToHisService service = AppContextHolder.getBean("recipeToHisService", RecipeToHisService.class);
            List<Integer> emptyOrganCode = new ArrayList<>();
            for (Recipedetail detail : detailList) {
                if (StringUtils.isEmpty(detail.getOrganDrugCode())) {
                    emptyOrganCode.add(detail.getDrugId());
                }
            }
            if (CollectionUtils.isNotEmpty(emptyOrganCode)) {
                LOGGER.error("scanDrugStock 医院配置药品存在编号为空的数据. drugIdList={}", JSONUtils.toString(emptyOrganCode));
                result.setCode(RecipeResultBean.FAIL);
                result.setError("医院配置药品存在编号为空的数据");
                return result;
            }

            DrugInfoResponseTO response = service.scanDrugStock(detailList, recipe.getClinicOrgan());
            if (null == response) {
                //his未配置该服务则还是可以通过
//                result.setCode(RecipeResultBean.FAIL);
                result.setError("HIS返回为NULL");
            } else {
                if (!Integer.valueOf(0).equals(response.getMsgCode())) {
                    String organCodeStr = response.getMsg();
                    List<String> nameList = new ArrayList<>();
                    if (StringUtils.isNotEmpty(organCodeStr)) {
                        List<String> organCodes = Arrays.asList(organCodeStr.split(","));
                        nameList = organDrugListDAO.findNameByOrganIdAndDrugCodes(recipe.getClinicOrgan(), organCodes);
                    }
                    String showMsg = "由于" + Joiner.on(",").join(nameList) + "门诊药房库存不足，该处方仅支持配送，无法到院取药，是否继续？";
                    result.setCode(RecipeResultBean.FAIL);
                    result.setError(showMsg.toString());
                    result.setExtendValue("1");
                    LOGGER.error("scanDrugStock 存在无库存药品. response={} ", JSONUtils.toString(response));
                }
            }
        } else {
            result.setCode(RecipeResultBean.FAIL);
            result.setError("医院HIS未启用。");
            LOGGER.error("scanDrugStock 医院HIS未启用[organId:" + recipe.getClinicOrgan() + ",recipeId:" + recipe.getRecipeId() + "]");
        }

        return result;
    }

    /**
     * 发送药师审核结果
     * @param recipe
     * @return
     */
    public RecipeResultBean recipeAudit(Recipe recipe, CheckYsInfoBean resutlBean){
        RecipeResultBean result = RecipeResultBean.getSuccess();
        if (isHisEnable(recipe.getClinicOrgan())) {
            RecipeToHisService service = AppContextHolder.getBean("recipeToHisService", RecipeToHisService.class);
            RecipeAuditReqTO request = HisRequestInit.recipeAudit(recipe, resutlBean);
            service.recipeAudit(request);
            return result;
        } else {
            result.setCode(RecipeResultBean.FAIL);
            result.setError("医院HIS未启用。");
            LOGGER.error("recipeAudit 医院HIS未启用[organId:" + recipe.getClinicOrgan() + ",recipeId:" + recipe.getRecipeId() + "]");
        }

        return result;
    }

    /**
     * 发送处方电子病历
     * @param recipeId
     * @return
     */
    public RecipeResultBean docIndexToHis(Integer recipeId){
        RecipeResultBean result = RecipeResultBean.getSuccess();
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        Recipe recipe = recipeDAO.getByRecipeId(recipeId);
        if (null == recipe) {
            result.setCode(RecipeResultBean.FAIL);
            result.setError("找不到处方");
            return result;
        }
        if (isHisEnable(recipe.getClinicOrgan())) {
            RecipeToHisService service = AppContextHolder.getBean("recipeToHisService", RecipeToHisService.class);
            DocIndexToHisReqTO request = HisRequestInit.initDocIndexToHisReqTO(recipe);
            service.docIndexToHis(request);
            return result;
        } else {
            result.setCode(RecipeResultBean.FAIL);
            result.setError("医院HIS未启用。");
            LOGGER.error("docIndexToHis 医院HIS未启用[organId:" + recipe.getClinicOrgan() + ",recipeId:" + recipe.getRecipeId() + "]");
        }

        return result;
    }

    /**
     * 判断是否需要对接HIS----根据运营平台配置处方类型是否跳过his
     *
     * @param recipe
     * @return
     */
    private boolean skipHis(Recipe recipe) {
        try {
            IConfigurationCenterUtilsService configurationCenterUtilsService = ApplicationUtils.getBaseService(IConfigurationCenterUtilsService.class);
            String[] recipeTypes = (String[])configurationCenterUtilsService.getConfiguration(recipe.getClinicOrgan(), "getRecipeTypeToHis");
            List<String> recipeTypelist = Arrays.asList(recipeTypes);
            if (recipeTypelist.contains(Integer.toString(recipe.getRecipeType()))) {
                return false;
            }
        }catch (Exception e){
            LOGGER.error("skipHis error "+ e.getMessage());
            //按原来流程走-西药中成药默认对接his
            if (!RecipeUtil.isTcmType(recipe.getRecipeType())) {
                return false;
            }
        }
        return true;
    }

    /**
     * 判断his是否存在
     *
     * @param sendOrganId
     * @return
     */
    private boolean isHisEnable(Integer sendOrganId) {
        IHisConfigService iHisConfigService = ApplicationUtils.getBaseService(IHisConfigService.class);
        return iHisConfigService.isHisEnable(sendOrganId);
    }

    public boolean hisRecipeCheck(Map<String, Object> rMap, RecipeBean recipeBean) {
        RecipeDetailDAO detailDAO = DAOFactory.getDAO(RecipeDetailDAO.class);
        List<Recipedetail> details = detailDAO.findByRecipeId(recipeBean.getRecipeId());

        HisCheckRecipeReqTO hisCheckRecipeReqTO = new HisCheckRecipeReqTO();
        OrganService organService = BasicAPI.getService(OrganService.class);
        DepartmentService departmentService = BasicAPI.getService(DepartmentService.class);
        hisCheckRecipeReqTO.setClinicOrgan(recipeBean.getClinicOrgan());
        hisCheckRecipeReqTO.setOrganID(organService.getOrganizeCodeByOrganId(recipeBean.getClinicOrgan()));
        if (recipeBean.getClinicId() != null){
            hisCheckRecipeReqTO.setClinicID(recipeBean.getClinicId().toString());
            IHosrelationService hosrelationService = BaseAPI.getService(IHosrelationService.class);
            //挂号记录
            HosrelationBean hosrelation = hosrelationService.getByBusIdAndBusType(recipeBean.getClinicId(), BusTypeEnum.CONSULT.getId());
            if (hosrelation != null && StringUtils.isNotEmpty(hosrelation.getRegisterId())){
                hisCheckRecipeReqTO.setClinicID(hosrelation.getRegisterId());
            }
        }
        hisCheckRecipeReqTO.setRecipeID(recipeBean.getRecipeCode());
        IPatientService iPatientService = ApplicationUtils.getBaseService(IPatientService.class);
        PatientBean patientBean = iPatientService.get(recipeBean.getMpiid());
        if (null != patientBean) {
            //身份证
            hisCheckRecipeReqTO.setCertID(patientBean.getIdcard());
            //患者名
            hisCheckRecipeReqTO.setPatientName(patientBean.getPatientName());
            //患者性别
            hisCheckRecipeReqTO.setPatientSex(patientBean.getPatientSex());
            //病人类型
        }
        //医生工号
        IEmploymentService iEmploymentService = ApplicationUtils.getBaseService(IEmploymentService.class);
        if (recipeBean.getDoctor() != null){
            String jobNumber = iEmploymentService.getJobNumberByDoctorIdAndOrganIdAndDepartment(recipeBean.getDoctor(), recipeBean.getClinicOrgan(), recipeBean.getDepart());
            hisCheckRecipeReqTO.setDoctorID(jobNumber);
        }
        //处方数量
        hisCheckRecipeReqTO.setRecipeNum("1");
        //诊断代码
        hisCheckRecipeReqTO.setIcdCode(RecipeUtil.getCode(recipeBean.getOrganDiseaseId()));
        //诊断名称
        hisCheckRecipeReqTO.setIcdName(RecipeUtil.getCode(recipeBean.getOrganDiseaseName()));
        //科室代码---行政科室代码
        DepartmentDTO departmentDTO = departmentService.getById(recipeBean.getDepart());
        if (departmentDTO!=null){
            hisCheckRecipeReqTO.setDeptCode(departmentDTO.getCode());
        }
        //开单时间
        hisCheckRecipeReqTO.setRecipeDate(DateConversion.formatDateTimeWithSec(recipeBean.getSignDate()));
        //处方类别
        hisCheckRecipeReqTO.setRecipeType(String.valueOf(recipeBean.getRecipeType()));
        //处方金额
        hisCheckRecipeReqTO.setRecipePrice(recipeBean.getTotalMoney());
        //orderList
        List<RecipeOrderItemTO> list = Lists.newArrayList();
        DrugListDAO drugListDAO = DAOFactory.getDAO(DrugListDAO.class);
        if (null != details && !details.isEmpty()) {
            for (Recipedetail detail : details) {
                RecipeOrderItemTO item = new RecipeOrderItemTO();
                item.setDosage((null != detail.getUseDose()) ? Double
                        .toString(detail.getUseDose()) : null);
                item.setDrcode(detail.getOrganDrugCode());
                item.setDrname(detail.getDrugName());
                item.setDrugManf(drugListDAO.getById(detail.getDrugId()).getProducer());
                //频次
                item.setFrequency(UsingRateFilter.filterNgari(recipeBean.getClinicOrgan(),detail.getUsingRate()));
                //用法
                item.setAdmission(UsePathwaysFilter.filterNgari(recipeBean.getClinicOrgan(),detail.getUsePathways()));
                //用药天数
                item.setUseDays(Integer.toString(detail.getUseDays()));
                //剂量单位
                item.setDrunit(detail.getUseDoseUnit());
                // 开药数量
                item.setTotalDose((null != detail.getUseTotalDose()) ? Double
                        .toString(detail.getUseTotalDose()) : null);
                //药品单位
                item.setUnit(detail.getDrugUnit());
                //药品规格
                item.setDrModel(detail.getDrugSpec());
                //药品包装
                item.setPack(String.valueOf(detail.getPack()));
                //药品包装单位
                item.setPackUnit(detail.getDrugUnit());
                list.add(item);
            }
            hisCheckRecipeReqTO.setOrderList(list);
        }

        RecipeToHisService service = AppContextHolder.getBean("recipeToHisService", RecipeToHisService.class);
        HisResponseTO hisResult = service.hisCheckRecipe(hisCheckRecipeReqTO);
        LOGGER.info("hisRecipeCheck request={} result={}", JSONUtils.toString(hisCheckRecipeReqTO),JSONUtils.toString(hisResult));
        if (hisResult==null){
            rMap.put("signResult", false);
            rMap.put("errorFlag",true);
            rMap.put("errorMsg", "his返回结果null");
            return false;
        }
        if ("200".equals(hisResult.getMsgCode())){
            Map<String,String> map = (Map<String,String>)hisResult.getData();
            if ("0".equals(map.get("checkResult"))){
                rMap.put("signResult", false);
                rMap.put("errorFlag",true);
                rMap.put("errorMsg", map.get("resultMark"));
            }else {
                return "1".equals(map.get("checkResult"));

            }
        }else {
            rMap.put("signResult", false);
            rMap.put("errorFlag",true);
            rMap.put("errorMsg",hisResult.getMsg());
        }
        return false;
    }

    /**
     * 武昌基础药品数据同步给his
     * @param drugLists
     */
    @RpcService
    public void syncDrugListToHis(List<DrugList> drugLists){
        RecipeToHisService service = AppContextHolder.getBean("recipeToHisService", RecipeToHisService.class);
        OrganService organService = ApplicationUtils.getBasicService(OrganService.class);

        List<DrugList> list = Lists.newArrayList();
        for (DrugList drugList : drugLists) {
            //武昌机构用的药品基础药品数据sourceorgan都为1001780
            if (drugList.getSourceOrgan() == 1001780){
                //double失真
                drugList.setUseDose(BigDecimal.valueOf(drugList.getUseDose()).doubleValue());
                drugList.setPrice1(BigDecimal.valueOf(drugList.getPrice1()).doubleValue());
                drugList.setPrice2(drugList.getPrice1());
               list.add(drugList);
            }
        }
        if (CollectionUtils.isEmpty(list)){
            return;
        }
        //武昌机构集合
        Set<String> organIdList = redisClient.sMembers(CacheConstant.KEY_WUCHANG_ORGAN_LIST);
        SyncDrugListToHisReqTO request;
        List<DrugListTO> drugListTO = ObjectCopyUtils.convert(list, DrugListTO.class);
        for (String organId : organIdList){
            request = new SyncDrugListToHisReqTO();
            request.setClinicOrgan(Integer.valueOf(organId));
            //组织机构编码
            request.setOrganCode(organService.getOrganizeCodeByOrganId(Integer.valueOf(organId)));
            request.setDrugList(drugListTO);
            service.syncDrugListToHis(request);
        }
    }
}
