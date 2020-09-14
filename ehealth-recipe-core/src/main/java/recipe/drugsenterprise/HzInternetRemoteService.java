package recipe.drugsenterprise;

import com.alibaba.fastjson.JSON;
import com.alijk.bqhospital.alijk.conf.TaobaoConf;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.ngari.base.BaseAPI;
import com.ngari.base.common.ICommonService;
import com.ngari.base.organ.model.OrganBean;
import com.ngari.base.organ.service.IOrganService;
import com.ngari.common.mode.HisResponseTO;
import com.ngari.his.recipe.mode.DeliveryList;
import com.ngari.his.recipe.mode.MedicalPreSettleReqNTO;
import com.ngari.his.recipe.mode.MedicalPreSettleReqTO;
import com.ngari.his.recipe.mode.RecipeMedicalPreSettleInfo;
import com.ngari.patient.dto.OrganDTO;
import com.ngari.patient.dto.PatientDTO;
import com.ngari.patient.service.BasicAPI;
import com.ngari.patient.service.HealthCardService;
import com.ngari.patient.service.OrganService;
import com.ngari.patient.service.PatientService;
import com.ngari.recipe.drugsenterprise.model.DrugsDataBean;
import com.ngari.recipe.entity.DrugsEnterprise;
import com.ngari.recipe.entity.Recipe;
import com.ngari.recipe.entity.RecipeExtend;
import com.ngari.recipe.entity.RecipeOrder;
import com.ngari.recipe.hisprescription.model.HospitalRecipeDTO;
import com.ngari.recipe.recipe.model.RecipeBean;
import ctd.controller.exception.ControllerException;
import ctd.dictionary.DictionaryController;
import ctd.persistence.DAOFactory;
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
import recipe.bean.RecipePayModeSupportBean;
import recipe.constant.DrugEnterpriseConstant;
import recipe.dao.DrugsEnterpriseDAO;
import recipe.dao.RecipeDAO;
import recipe.dao.RecipeExtendDAO;
import recipe.drugsenterprise.compatible.HzInternetRemoteNewType;
import recipe.drugsenterprise.compatible.HzInternetRemoteOldType;
import recipe.drugsenterprise.compatible.HzInternetRemoteTypeInterface;
import recipe.hisservice.RecipeToHisService;
import recipe.service.RecipeLogService;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @description 杭州互联网（金投）对接服务
 * @author gmw
 * @date 2019/9/11
 */
@RpcBean("hzInternetRemoteService")
public class HzInternetRemoteService extends AccessDrugEnterpriseService{

    private static final Logger LOGGER = LoggerFactory.getLogger(HzInternetRemoteService.class);

    private static final String EXPIRE_TIP = "请重新授权";

    @Autowired
    private RecipeExtendDAO recipeExtendDAO;

    @Autowired
    private TaobaoConf taobaoConf;

    @Override
    public void tokenUpdateImpl(DrugsEnterprise drugsEnterprise) {
        LOGGER.info("HzInternetRemoteService tokenUpdateImpl not implement.");
    }

    @Override
    public String getDrugInventory(Integer drugId, DrugsEnterprise drugsEnterprise, Integer organId) {
        return "暂不支持库存查询";
    }

    @Override
    public List<String> getDrugInventoryForApp(DrugsDataBean drugsDataBean, DrugsEnterprise drugsEnterprise, Integer flag) {
        return null;
    }

    @Override
    public DrugEnterpriseResult pushRecipeInfo(List<Integer> recipeIds, DrugsEnterprise enterprise) {
        return getRealization(recipeIds).pushRecipeInfo(recipeIds, enterprise);
    }

    @Override
    public DrugEnterpriseResult pushRecipe(HospitalRecipeDTO hospitalRecipeDTO, DrugsEnterprise enterprise) {
        return DrugEnterpriseResult.getSuccess();
    }

    /*
     * @description 处方预结算
     * @author gaomw
     * @date 2019/12/13
     * @param [recipeId]
     * @return recipe.bean.DrugEnterpriseResult
     */
    @RpcService
    public DrugEnterpriseResult recipeMedicalPreSettleO(Integer recipeId) {
        DrugEnterpriseResult result = DrugEnterpriseResult.getSuccess();
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        Recipe recipe = recipeDAO.getByRecipeId(recipeId);
        MedicalPreSettleReqTO medicalPreSettleReqTO = new MedicalPreSettleReqTO();
        medicalPreSettleReqTO.setClinicOrgan(recipe.getClinicOrgan());

        //封装医保信息
        RecipeExtend recipeExtend = recipeExtendDAO.getByRecipeId(recipe.getRecipeId());
        if(recipeExtend != null && recipeExtend.getMedicalSettleData() != null){
            medicalPreSettleReqTO.setHospOrgCode(recipeExtend.getHospOrgCodeFromMedical());
            medicalPreSettleReqTO.setInsuredArea(recipeExtend.getInsuredArea());
            medicalPreSettleReqTO.setMedicalSettleData(recipeExtend.getMedicalSettleData());
        } else {
            LOGGER.info("杭州互联网虚拟药企-未获取处方医保结算请求串-recipeId={}", JSONUtils.toString(recipe.getRecipeId()));
            result.setMsg("未获取处方医保结算请求串");
            result.setCode(DrugEnterpriseResult.FAIL);
        }

        RecipeToHisService service = AppContextHolder.getBean("recipeToHisService", RecipeToHisService.class);
        //HisResponseTO hisResult = service.recipeMedicalPreSettle(medicalPreSettleReqTO);
        HisResponseTO hisResult = null;
        if(hisResult != null && "200".equals(hisResult.getMsgCode())){
            LOGGER.info("杭州互联网虚拟药企-处方预结算成功-his. param={},result={}", JSONUtils.toString(medicalPreSettleReqTO), JSONUtils.toString(hisResult));
            result.setCode(DrugEnterpriseResult.SUCCESS);
        }else{
            LOGGER.error("杭州互联网虚拟药企-处方预结算失败-his. param={},result={}", JSONUtils.toString(medicalPreSettleReqTO), JSONUtils.toString(hisResult));
            if(hisResult != null){
                result.setMsg(hisResult.getMsg());
            }
            result.setCode(DrugEnterpriseResult.FAIL);
        }
        return result;
    }


    /*
     * @description 处方预结算(新)
     * @author gaomw
     * @date 2019/12/13
     * @param [recipeId]
     * @return recipe.bean.DrugEnterpriseResult
     */
    @RpcService
    public DrugEnterpriseResult recipeMedicalPreSettle(Integer recipeId, Integer depId) {

        DrugEnterpriseResult result = DrugEnterpriseResult.getSuccess();
        if(recipeId == null || depId == null){
            LOGGER.info("recipeMedicalPreSettle-未获取处方或药企ID,处方ID={},药企ID：{}",recipeId,depId);
        } else {
            LOGGER.info("recipeMedicalPreSettle-杭州互联网医保预结算开始,处方号={},药企ID：{}",recipeId,depId);
            RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
            Recipe recipe = recipeDAO.getByRecipeId(recipeId);

            DrugsEnterpriseDAO drugEnterpriseDao = DAOFactory.getDAO(DrugsEnterpriseDAO.class);
            DrugsEnterprise drugEnterprise = drugEnterpriseDao.get(depId);
            if(drugEnterprise != null && "hzInternet".equals(drugEnterprise.getAccount())){
                HealthCardService healthCardService = ApplicationUtils.getBasicService(HealthCardService.class);
                //杭州市互联网医院监管中心 管理单元eh3301
                OrganService organService = ApplicationUtils.getBasicService(OrganService.class);
                OrganDTO organDTO = organService.getByManageUnit("eh3301");
                String bxh = null;
                if (organDTO!=null) {
                    bxh = healthCardService.getMedicareCardId(recipe.getMpiid(), organDTO.getOrganId());

                }
                //有市名卡才走预结算
                if (StringUtils.isNotEmpty(bxh)){
                    PatientService patientService = BasicAPI.getService(PatientService.class);
                    PatientDTO patientBean = patientService.get(recipe.getMpiid());

                    MedicalPreSettleReqNTO request = new MedicalPreSettleReqNTO();
                    request.setClinicId(String.valueOf(recipe.getClinicId()));
                    request.setClinicOrgan(recipe.getClinicOrgan());
                    request.setPatientName(patientBean.getPatientName());
                    request.setIdcard(patientBean.getIdcard());
                    request.setBirthday(patientBean.getBirthday());
                    request.setAddress(patientBean.getAddress());
                    request.setMobile(patientBean.getMobile());
                    request.setGuardianName(patientBean.getGuardianName());
                    request.setGuardianTel(patientBean.getLinkTel());
                    request.setGuardianCertificate(patientBean.getGuardianCertificate());
                    request.setRecipeId(recipeId + "");

                    request.setDoctorId(recipe.getDoctor() + "");
                    request.setDoctorName(recipe.getDoctorName());
                    request.setDepartId(recipe.getDepart() + "");

                    //默认是医保，医生选择了自费时，强制设置为自费
                    RecipeExtend recipeExtend = recipeExtendDAO.getByRecipeId(recipe.getRecipeId());
                    if(recipeExtend != null && recipeExtend.getMedicalType() != null && "0".equals(recipeExtend.getMedicalType())){
                        request.setIszfjs("1");
                    } else {
                        request.setIszfjs("0");
                    }
                    request.setBxh(bxh);

                    try {
                        request.setSex(DictionaryController.instance().get("eh.base.dictionary.Gender").getText(patientBean.getPatientSex()));
                        request.setDepartName(DictionaryController.instance().get("eh.base.dictionary.Depart").getText(recipe.getDepart()));
                    } catch (ControllerException e) {
                        LOGGER.error("DictionaryController 字典转化异常,{}",e);
                    }
                    RecipeToHisService service = AppContextHolder.getBean("recipeToHisService", RecipeToHisService.class);
                    LOGGER.info("recipeMedicalPreSettle. recipeId={},req={}", recipeId, JSONUtils.toString(request));
                    HisResponseTO<RecipeMedicalPreSettleInfo> hisResult = service.recipeMedicalPreSettleN(request);
                    if(hisResult != null && "200".equals(hisResult.getMsgCode())){
                        LOGGER.info("recipeMedicalPreSettle-success. recipeId={},result={}", recipeId, JSONUtils.toString(hisResult));
                        if(hisResult.getData() != null){
                            if(recipeExtend != null){
                                Map<String, String> map = new HashMap<String, String>();
                                map.put("registerNo", hisResult.getData().getGhxh());
                                map.put("hisSettlementNo", hisResult.getData().getSjh());
                                map.put("preSettleTotalAmount", hisResult.getData().getZje());
                                map.put("fundAmount", hisResult.getData().getYbzf());
                                map.put("cashAmount", hisResult.getData().getYfje());
                                //map.put("medicalSettleData", hisResult.getData().getMedicalRespData());
                                recipeExtendDAO.updateRecipeExInfoByRecipeId(recipe.getRecipeId(), map);
                            } else {
                                recipeExtend = new RecipeExtend();
                                recipeExtend.setRecipeId(recipe.getRecipeId());
                                recipeExtend.setRegisterNo(hisResult.getData().getGhxh());
                                recipeExtend.setHisSettlementNo(hisResult.getData().getSjh());
                                recipeExtend.setPreSettletotalAmount(hisResult.getData().getZje());
                                recipeExtend.setFundAmount(hisResult.getData().getYbzf());
                                recipeExtend.setCashAmount(hisResult.getData().getYfje());
                                recipeExtendDAO.save(recipeExtend);
                            }
                        }
                        result.setCode(DrugEnterpriseResult.SUCCESS);
                        RecipeLogService.saveRecipeLog(recipe.getRecipeId(), recipe.getStatus(), recipe.getStatus(), "杭州市医保预结算成功");
                    }else{
                        LOGGER.error("recipeMedicalPreSettle fail. recipeId={},result={}", recipeId, JSONUtils.toString(hisResult));
                        String msg;
                        if(hisResult != null){
                            msg = hisResult.getMsg();
                        }else {
                            msg = "前置机返回结果null";
                        }
                        result.setCode(DrugEnterpriseResult.FAIL);
                        result.setMsg(msg);
                        RecipeLogService.saveRecipeLog(recipe.getRecipeId(), recipe.getStatus(), recipe.getStatus(), "杭州市医保预结算失败，原因："+msg);
                    }
                } else{
                    LOGGER.error("recipeMedicalPreSettle-患者医保卡号为null,recipeId{}，患者：{}", recipe.getRecipeId(), recipe.getPatientName());
                    RecipeLogService.saveRecipeLog(recipe.getRecipeId(), recipe.getStatus(), recipe.getStatus(), "由于获取不到杭州市医保卡,无法进行预结算");
                }
            }  else{
                LOGGER.info("recipeMedicalPreSettle-非杭州互联网药企不走杭州医保预结算,recipeId={} 药企ID：{}",recipeId,depId);
            }
        }

        return result;
    }

    //date 20200318
    //确认订单前校验处方信息
    @RpcService
    public DrugEnterpriseResult checkMakeOrder(Integer recipeId, Map<String, String> extInfo) {
        LOGGER.info("checkMakeOrder 当前确认订单校验的新流程预结算->同步配送信息, 入参：{}，{}",
                recipeId, JSONUtils.toString(extInfo));
        DrugEnterpriseResult result = DrugEnterpriseResult.getSuccess();

        //当医保支付开关端配置关闭时才走老逻辑
        ICommonService commonService = BaseAPI.getService(ICommonService.class);
        Boolean medicalPayConfig = (Boolean) commonService.getClientConfigByKey("medicalPayConfig");
        if (medicalPayConfig) {
            result = recipeMedicalPreSettle(recipeId, null == extInfo.get("depId") ? null : Integer.parseInt(extInfo.get("depId").toString()));
            if (DrugEnterpriseResult.FAIL.equals(result.getCode())) {
                LOGGER.info("order 当前处方{}确认订单校验处方信息：预结算失败，结算结果：{}", recipeId, JSONUtils.toString(result));
                return result;
            }
        }

        RemoteDrugEnterpriseService remoteDrugEnterpriseService =
                ApplicationUtils.getRecipeService(RemoteDrugEnterpriseService.class);
        DrugsEnterpriseDAO drugsEnterpriseDAO = DAOFactory.getDAO(DrugsEnterpriseDAO.class);

        //判断当前确认订单是配送方式
        if(StringUtils.isEmpty(extInfo.get("depId")) || StringUtils.isEmpty(extInfo.get("payMode"))){
            LOGGER.info("order 当前处方{}确认订单校验处方信息,没有传递配送药企信息，无需同步配送信息，直接返回预结算结果",
                    recipeId);
            return result;
        }
        DrugsEnterprise drugsEnterprise = drugsEnterpriseDAO.getById(Integer.parseInt(extInfo.get("depId")));
        AccessDrugEnterpriseService remoteService = remoteDrugEnterpriseService.getServiceByDep(drugsEnterprise);
        return remoteService.sendMsgResultMap(recipeId, extInfo, result);

    }

    @Override
    public DrugEnterpriseResult scanStock(Integer recipeId, DrugsEnterprise drugsEnterprise) {
        return getRealization(Lists.newArrayList(recipeId)).scanStock(recipeId, drugsEnterprise);
    }

    private boolean valiScanStock(Integer recipeId, DrugsEnterprise drugsEnterprise, DrugEnterpriseResult result) {
        if(null == recipeId){
            result.setCode(DrugEnterpriseResult.FAIL);
            result.setError("传入的处方id为空！");
            return false;
        }
        return true;
    }

    @Override
    public DrugEnterpriseResult syncEnterpriseDrug(DrugsEnterprise drugsEnterprise, List<Integer> drugIdList) {
        return DrugEnterpriseResult.getSuccess();
    }

    @Override
    public DrugEnterpriseResult pushCheckResult(Integer recipeId, Integer checkFlag, DrugsEnterprise enterprise) {
        return DrugEnterpriseResult.getSuccess();
    }

    @Override
    public DrugEnterpriseResult findSupportDep(List<Integer> recipeIds, Map ext, DrugsEnterprise enterprise) {
        return getRealization(recipeIds).findSupportDep(recipeIds, ext, enterprise);
    }

    private Boolean valiRequestDate(List<Integer> recipeIds, Map ext, DrugEnterpriseResult result) {
        if (CollectionUtils.isEmpty(recipeIds)) {
            result.setCode(DrugEnterpriseResult.FAIL);
            result.setError("传入的处方id为空！");
            return false;
        }

        return true;
    }

    @Override
    public String getDrugEnterpriseCallSys() {
        return DrugEnterpriseConstant.COMPANY_HZ;
    }

    /*
     * @description 推送药企处方状态，由于只是个别药企需要实现，故有默认实现
     * @author gmw
     * @date 2019/9/18
     * @param rxId  recipeCode
     * @param status  status
     * @return recipe.bean.DrugEnterpriseResult
     */
    @RpcService
    @Override
    public DrugEnterpriseResult updatePrescriptionStatus(String rxId, int status) {
        LOGGER.info("更新处方状态");
        DrugEnterpriseResult drugEnterpriseResult = new DrugEnterpriseResult(DrugEnterpriseResult.SUCCESS);

        return drugEnterpriseResult;
    }

//    /**
//     *
//     * @param rxId  处⽅Id
//     * @param queryOrder  是否查询订单
//     * @return 处方单
//     */
//    @Override
//    public DrugEnterpriseResult queryPrescription(String rxId, Boolean queryOrder) {
//        PatientService patientService = BasicAPI.getService(PatientService.class);
//        OrganService organService = BasicAPI.getService(OrganService.class);
//        DrugEnterpriseResult drugEnterpriseResult = new DrugEnterpriseResult(DrugEnterpriseResult.SUCCESS);
//        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
//        Recipe dbRecipe = recipeDAO.getByRecipeCode(rxId);
//        if (ObjectUtils.isEmpty(dbRecipe)) {
//            return getDrugEnterpriseResult(drugEnterpriseResult, "处方不存在");
//        }
//        String outHospitalId = organService.getOrganizeCodeByOrganId(dbRecipe.getClinicOrgan());
//        if (StringUtils.isEmpty(outHospitalId)) {
//            return getDrugEnterpriseResult(drugEnterpriseResult, "医院的外部编码不能为空");
//        }
//        String loginId = patientService.getLoginIdByMpiId(dbRecipe.getRequestMpiId());
//        String accessToken = aldyfRedisService.getTaobaoAccessToken(loginId);
//        if (ObjectUtils.isEmpty(accessToken)) {
//            return getDrugEnterpriseResult(drugEnterpriseResult, EXPIRE_TIP);
//        }
//        LOGGER.info("获取到accessToken:{}, loginId:{},{},{}", accessToken, loginId, rxId, outHospitalId);
//        alihealthHospitalService.setTopSessionKey(accessToken);
//        AlibabaAlihealthRxPrescriptionGetRequest prescriptionGetRequest = new AlibabaAlihealthRxPrescriptionGetRequest();
//        prescriptionGetRequest.setRxId(rxId);
//        prescriptionGetRequest.setOutHospitalId(outHospitalId);
//        BaseResult<AlibabaAlihealthRxPrescriptionGetResponse> responseBaseResult = alihealthHospitalService.queryPrescription(prescriptionGetRequest);
//        LOGGER.info("查询处方，{}", getJsonLog(responseBaseResult));
//        getAldyfResult(drugEnterpriseResult, responseBaseResult);
//        return drugEnterpriseResult;
//    }

    /**
     * 返回调用信息
     * @param result DrugEnterpriseResult
     * @param msg     提示信息
     * @return DrugEnterpriseResult
     */
    private DrugEnterpriseResult getDrugEnterpriseResult(DrugEnterpriseResult result, String msg) {
        result.setMsg(msg);
        result.setCode(DrugEnterpriseResult.FAIL);
        LOGGER.info("HzInternetRemoteService-getDrugEnterpriseResult提示信息：{}.", msg);
        return result;
    }

    private Integer getClinicOrganByOrganId(String organId, String clinicOrgan) throws Exception {
        Integer co = null;
        if (StringUtils.isEmpty(clinicOrgan)) {
            IOrganService organService = BaseAPI.getService(IOrganService.class);

            List<OrganBean> organList = organService.findByOrganizeCode(organId);
            if (CollectionUtils.isNotEmpty(organList)) {
                co = organList.get(0).getOrganId();
            }
        } else {
            co = Integer.parseInt(clinicOrgan);
        }
        return co;
    }

    private static String getJsonLog(Object object) {
        return JSONUtils.toString(object);
    }

    @Override
    public boolean scanStock(Recipe dbRecipe, DrugsEnterprise dep, List<Integer> drugIds) {
        return getRealization(dbRecipe).scanStock(dbRecipe, dep, drugIds);
    }

    @Override
    public String appEnterprise(RecipeOrder order) {
        return getRealization(order).appEnterprise(order);
    }

    @Override
    public BigDecimal orderToRecipeFee(RecipeOrder order, List<Integer> recipeIds, RecipePayModeSupportBean payModeSupport, BigDecimal recipeFee, Map<String, String> extInfo) {
        return getRealization(order).orderToRecipeFee(order, recipeIds, payModeSupport, recipeFee, extInfo);
    }

    @Override
    public void setOrderEnterpriseMsg(Map<String, String> extInfo, RecipeOrder order) {
        getRealization(order).setOrderEnterpriseMsg(extInfo, order);
    }

    @Override
    public void checkRecipeGiveDeliveryMsg(RecipeBean recipeBean, Map<String, Object> map) {
        LOGGER.info("checkRecipeGiveDeliveryMsg recipeBean:{}, map:{}", JSONUtils.toString(recipeBean), JSONUtils.toString(map));
        String giveMode = null != map.get("giveMode") ? map.get("giveMode").toString() : null;
        Object deliveryList = map.get("deliveryList");
        if(null != deliveryList && null != giveMode){

            List<Map> deliveryLists = (List<Map>)deliveryList;
            //暂时按照逻辑只保存展示返回的第一个药企
            DeliveryList nowDeliveryList = JSON.parseObject(JSON.toJSONString(deliveryLists.get(0)), DeliveryList.class);
            RecipeExtendDAO recipeExtendDAO = DAOFactory.getDAO(RecipeExtendDAO.class);
            if (null != nowDeliveryList){
                Map<String,String> updateMap = Maps.newHashMap();
                updateMap.put("deliveryCode", nowDeliveryList.getDeliveryCode());
                updateMap.put("deliveryName", nowDeliveryList.getDeliveryName());
                //存放处方金额
                updateMap.put("deliveryRecipeFee", null != nowDeliveryList.getRecipeFee() ? nowDeliveryList.getRecipeFee().toString() : null);
                recipeExtendDAO.updateRecipeExInfoByRecipeId(recipeBean.getRecipeId(), updateMap);
            }
            //date 20200311
            //将his返回的批量药企信息存储下来，将信息分成|分割
            DeliveryList deliveryListNow;
            Map<String,String> updateMap = Maps.newHashMap();
            StringBuffer deliveryCodes = new StringBuffer().append("|");
            StringBuffer deliveryNames = new StringBuffer().append("|");
            StringBuffer deliveryRecipeFees = new StringBuffer().append("|");
            for(Map<String,String> delivery : deliveryLists){
                deliveryListNow = JSON.parseObject(JSON.toJSONString(delivery), DeliveryList.class);
                deliveryCodes.append(deliveryListNow.getDeliveryCode()).append("|");
                deliveryNames.append(deliveryListNow.getDeliveryName()).append("|");
                deliveryRecipeFees.append(deliveryListNow.getRecipeFee()).append("|");
            }
            updateMap.put("deliveryCode", "|".equals(deliveryCodes) ? null : deliveryCodes.toString());
            updateMap.put("deliveryName", "|".equals(deliveryNames) ? null : deliveryNames.toString());
            //存放处方金额
            updateMap.put("deliveryRecipeFee", "|".equals(deliveryRecipeFees) ? null : deliveryRecipeFees.toString());
            recipeExtendDAO.updateRecipeExInfoByRecipeId(recipeBean.getRecipeId(), updateMap);
            LOGGER.info("hisRecipeCheck 当前处方{}预校验，配送方式存储成功:{}！", recipeBean.getRecipeId(), JSONUtils.toString(updateMap));

        }else{
            LOGGER.info("hisRecipeCheck 当前处方{}预校验，配送方式没有返回药企信息！", recipeBean.getRecipeId());
        }
    }

    @Override
    public void setEnterpriseMsgToOrder(RecipeOrder order, Integer depId, Map<String, String> extInfo) {
        getRealization(order).setEnterpriseMsgToOrder(order, depId, extInfo);
    }

    @Override
    public Boolean specialMakeDepList(DrugsEnterprise drugsEnterprise, Recipe dbRecipe) {
        return getRealization(dbRecipe).specialMakeDepList(drugsEnterprise, dbRecipe);
    }

    @Override
    public void sendDeliveryMsgToHis(Integer recipeId) {
        getRealization(Lists.newArrayList(recipeId)).sendDeliveryMsgToHis(recipeId);
    }

    @Override
    public DrugEnterpriseResult sendMsgResultMap(Integer recipeId, Map<String, String> extInfo, DrugEnterpriseResult payResult) {
        return getRealization(Lists.newArrayList(recipeId)).sendMsgResultMap(recipeId, extInfo, payResult);
    }

    private HzInternetRemoteTypeInterface getRealization(List<Integer> recipeIds){
        //判断其对应的模式(旧/新模式)
        //根据当前处方的recipecode
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        HzInternetRemoteTypeInterface result = new HzInternetRemoteOldType();
        if(null != recipeIds && CollectionUtils.isNotEmpty(recipeIds)){
            Recipe recipe = recipeDAO.get(recipeIds.get(0));
            //当recipe没有关联上纳里平台的处方code说明是his同步过来的新流程
            if(null != recipe && null != recipe.getRecipeCode() && !recipe.getRecipeCode().contains("ngari")){
                result =  new HzInternetRemoteNewType();
            }
        }
        return result;
    }

    private HzInternetRemoteTypeInterface getRealization(RecipeOrder order){
        List<Integer> recipeIdList = JSONUtils.parse(order.getRecipeIdList(), List.class);
        if(null != recipeIdList && CollectionUtils.isNotEmpty(recipeIdList)){
            return getRealization(Lists.newArrayList(recipeIdList.get(0)));
        }
        return new HzInternetRemoteOldType();
    }

    private HzInternetRemoteTypeInterface getRealization(RecipeBean recipeBean){
        if(null != recipeBean){
            return getRealization(Lists.newArrayList(recipeBean.getRecipeId()));
        }
        return new HzInternetRemoteOldType();
    }

    private HzInternetRemoteTypeInterface getRealization(Recipe recipe){
        if(null != recipe){
            return getRealization(Lists.newArrayList(recipe.getRecipeId()));
        }
        return new HzInternetRemoteOldType();
    }

}
