package recipe.serviceprovider.recipe.service;


import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Maps;
import com.ngari.his.base.PatientBaseInfo;
import com.ngari.his.recipe.mode.QueryRecipeRequestTO;
import com.ngari.his.recipe.mode.QueryRecipeResponseTO;
import com.ngari.his.recipe.mode.RecipeInfoTO;
import com.ngari.his.recipe.service.IRecipeHisService;
import com.ngari.patient.dto.PatientDTO;
import com.ngari.patient.service.PatientService;
import com.ngari.patient.utils.ObjectCopyUtils;
import com.ngari.recipe.RecipeAPI;
import com.ngari.recipe.common.RecipeBussReqTO;
import com.ngari.recipe.common.RecipeListReqTO;
import com.ngari.recipe.common.RecipeListResTO;
import com.ngari.recipe.entity.*;
import com.ngari.recipe.recipe.constant.RecipePayTextEnum;
import com.ngari.recipe.recipe.model.*;
import com.ngari.recipe.recipe.service.IRecipeService;
import com.ngari.recipe.recipeorder.model.RecipeOrderBean;
import ctd.controller.exception.ControllerException;
import ctd.dictionary.DictionaryController;
import ctd.persistence.DAOFactory;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.spring.AppDomainContext;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcBean;
import ctd.util.annotation.RpcService;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import recipe.ApplicationUtils;
import recipe.bean.DrugEnterpriseResult;
import recipe.bussutil.RecipeUtil;
import recipe.constant.RecipeBussConstant;
import recipe.constant.RecipeStatusConstant;
import recipe.constant.ReviewTypeConstant;
import recipe.dao.*;
import recipe.drugsenterprise.CommonRemoteService;
import recipe.drugsenterprise.TmdyfRemoteService;
import recipe.hisservice.RecipeToHisCallbackService;
import recipe.medicationguide.service.WinningMedicationGuideService;
import recipe.recipecheck.RecipeCheckService;
import recipe.service.*;
import recipe.serviceprovider.BaseService;
import recipe.util.DateConversion;
import recipe.util.MapValueUtil;

import java.math.BigDecimal;
import java.util.*;

/**
 * company: ngarihealth
 *
 * @author: 0184/yu_yun
 * @date:2017/7/31.
 */
@RpcBean("remoteRecipeService")
public class RemoteRecipeService extends BaseService<RecipeBean> implements IRecipeService {

    /**
     * LOGGER
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(RemoteRecipeService.class);

    @RpcService
    @Override
    public void sendSuccess(RecipeBussReqTO request) {
        RecipeToHisCallbackService service = ApplicationUtils.getRecipeService(RecipeToHisCallbackService.class);
        if (null != request.getData()) {
            HisSendResTO response = (HisSendResTO) request.getData();
            service.sendSuccess(response);
        }
    }

    @RpcService
    @Override
    public void sendFail(RecipeBussReqTO request) {
        RecipeToHisCallbackService service = ApplicationUtils.getRecipeService(RecipeToHisCallbackService.class);
        if (null != request.getData()) {
            HisSendResTO response = (HisSendResTO) request.getData();
            service.sendFail(response);
        }
    }

    @RpcService
    @Override
    public RecipeBean get(Object id) {
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        Recipe recipe = recipeDAO.get(id);
        return getBean(recipe, RecipeBean.class);
    }

    @RpcService
    @Override
    public boolean haveRecipeAuthority(int doctorId) {
        RecipeService service = ApplicationUtils.getRecipeService(RecipeService.class);
        Map<String, Object> map = service.openRecipeOrNot(doctorId);
        boolean rs = false;
        try {
            rs = (boolean) map.get("result");
        } catch (Exception e) {
            rs = false;
        }
        return rs;
    }

    @RpcService
    @Override
    public void afterMedicalInsurancePay(int recipeId, boolean success) {
        RecipeMsgService.doAfterMedicalInsurancePaySuccess(recipeId, success);
    }


    @RpcService
    @Override
    public RecipeListResTO<Integer> findDoctorIdSortByCount(RecipeListReqTO request) {
        LOGGER.info("findDoctorIdSortByCount request={}", JSONUtils.toString(request));
        RecipeListService service = ApplicationUtils.getRecipeService(RecipeListService.class);
        List<Integer> organIds = MapValueUtil.getList(request.getConditions(), "organIds");
        List<Integer> doctorIds = service.findDoctorIdSortByCount(request.getStart(), request.getLimit(), organIds);
        return RecipeListResTO.getSuccessResponse(doctorIds);
    }

    @RpcService
    @Override
    public boolean changeRecipeStatus(int recipeId, int afterStatus) {
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        return recipeDAO.updateRecipeInfoByRecipeId(recipeId, afterStatus, null);
    }

    @RpcService
    @Override
    public RecipeBean getByRecipeId(int recipeId) {
        return get(recipeId);
    }

    @RpcService
    @Override
    public long getUncheckedRecipeNum(int doctorId) {
        RecipeCheckService service = ApplicationUtils.getRecipeService(RecipeCheckService.class);
        return service.getUncheckedRecipeNum(doctorId);
    }

    @RpcService
    @Override
    public RecipeBean getByRecipeCodeAndClinicOrgan(String recipeCode, int clinicOrgan) {
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        Recipe recipe = recipeDAO.getByRecipeCodeAndClinicOrgan(recipeCode, clinicOrgan);
        return getBean(recipe, RecipeBean.class);
    }

    @RpcService
    @Override
    public void changePatientMpiId(String newMpiId, String oldMpiId) {
        RecipeService service = ApplicationUtils.getRecipeService(RecipeService.class);
        service.updatePatientInfoForRecipe(newMpiId, oldMpiId);
    }

    @Override
    public RecipeListResTO<RecipeRollingInfoBean> findLastesRecipeList(RecipeListReqTO request) {
        LOGGER.info("findLastesRecipeList request={}", JSONUtils.toString(request));
        RecipeListService service = ApplicationUtils.getRecipeService(RecipeListService.class);
        List<Integer> organIds = MapValueUtil.getList(request.getConditions(), "organIds");
        List<RecipeRollingInfoBean> recipeList = service.findLastesRecipeList(organIds, request.getStart(), request.getLimit());
        if (CollectionUtils.isEmpty(recipeList)) {
            recipeList = new ArrayList<>(0);
        }
        return RecipeListResTO.getSuccessResponse(recipeList);
    }

    @RpcService
    @Override
    public QueryResult<Map> findRecipesByInfo(Integer organId, Integer status,
                                              Integer doctor, String patientName, Date bDate, Date eDate, Integer dateType,
                                              Integer depart, int start, int limit, List<Integer> organIds, Integer giveMode,Integer fromflag,Integer recipeId) {
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        return recipeDAO.findRecipesByInfo(organId, status, doctor, patientName, bDate, eDate, dateType, depart, start, limit, organIds, giveMode,fromflag,recipeId);
    }

    @RpcService
    @Override
    public Map<String, Integer> getStatisticsByStatus(Integer organId,
                                                      Integer status, Integer doctor, String mpiid,
                                                      Date bDate, Date eDate, Integer dateType,
                                                      Integer depart, int start, int limit, List<Integer> organIds, Integer giveMode,Integer fromflag,Integer recipeId) {
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        return recipeDAO.getStatisticsByStatus(organId, status, doctor, mpiid, bDate, eDate, dateType, depart, start, limit, organIds, giveMode,fromflag,recipeId);
    }

    @RpcService
    @Override
    public Map<String, Object> findRecipeAndDetailsAndCheckById(int recipeId) {
        RecipeCheckService service = ApplicationUtils.getRecipeService(RecipeCheckService.class);
        return service.findRecipeAndDetailsAndCheckById(recipeId);
    }

    @RpcService
    @Override
    public List<Map> queryRecipesByMobile(List<String> mpis){
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        return recipeDAO.queryRecipesByMobile(mpis);
    }

    @RpcService
    @Override
    public List<Integer> findDoctorIdsByRecipeStatus(Integer status) {
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        return recipeDAO.findDoctorIdsByStatus(status);
    }

    @RpcService
    @Override
    public List<String> findPatientMpiIdForOp(List<String> mpiIds, List<Integer> organIds){
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        return recipeDAO.findPatientMpiIdForOp(mpiIds, organIds);
    }

    @RpcService
    @Override
    public List<String> findCommonDiseasByDoctorAndOrganId(int doctorId, int organId) {
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        return recipeDAO.findCommonDiseasByDoctorAndOrganId(doctorId, organId);
    }

    @RpcService
    @Override
    public List<String> findHistoryMpiIdsByDoctorId(int doctorId, Integer start, Integer limit) {
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        return recipeDAO.findHistoryMpiIdsByDoctorId(doctorId, start,limit);
    }

    @RpcService
    @Override
    public void synPatientStatusToRecipe(String mpiId) {
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        recipeDAO.updatePatientStatusByMpiId(mpiId);
    }

    @RpcService
    @Override
    public void saveRecipeDataFromPayment(RecipeBean recipeBean, List<RecipeDetailBean> recipeDetailBeans) {

        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        List<Recipedetail> recipedetails = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(recipeDetailBeans)) {
            for (RecipeDetailBean recipeDetailBean : recipeDetailBeans) {
                recipedetails.add(getBean(recipeDetailBean,Recipedetail.class));
            }
        }
        if (StringUtils.isEmpty(recipeBean.getRecipeMode())){
            recipeBean.setRecipeMode(RecipeBussConstant.RECIPEMODE_NGARIHEALTH);
        }
        if (recipeBean.getReviewType()==null){
            recipeBean.setReviewType(ReviewTypeConstant.Postposition_Check);
        }
        recipeDAO.updateOrSaveRecipeAndDetail(getBean(recipeBean,Recipe.class),recipedetails,false);
    }


    /**
     * 根据日期范围，机构归类的业务量(天，月)
     * @param startDate
     * @param endDate
     * @return
     */
    @RpcService
    @Override
    public HashMap<Integer, Long> getCountByDateAreaGroupByOrgan(final String startDate, final String endDate) {
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        return recipeDAO.getCountByDateAreaGroupByOrgan(startDate, endDate);
    }
    /**
     * 根据日期范围，机构归类的业务量(小时)
     * @param startDate
     * @param endDate
     * @return
     */
    @RpcService
    @Override
    public HashMap<Object,Integer> getCountByHourAreaGroupByOrgan(final Date startDate, final Date endDate) {
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        return recipeDAO.getCountByHourAreaGroupByOrgan(startDate, endDate);
    }

    /**
     *
     * @param organId
     * @param status
     * @param doctor
     * @param patientName
     * @param bDate
     * @param eDate
     * @param dateType
     * @param depart
     * @param organIds
     * @param giveMode
     * @param fromflag
     * @return
     */
    @RpcService
    @Override
    public List<Map> findRecipesByInfoForExcel(final Integer organId, final Integer status, final Integer doctor, final String patientName, final Date bDate,
                                        final Date eDate, final Integer dateType, final Integer depart, List<Integer> organIds, Integer giveMode,
                                        Integer fromflag,Integer recipeId){
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        return recipeDAO.findRecipesByInfoForExcel(organId,status,doctor,patientName,bDate,eDate,dateType,depart,organIds,giveMode,fromflag,recipeId);
    }

    /**
     * 春节2月17版本 JRK
     * 查询
     * @param organId
     * @param status
     * @param doctor
     * @param patientName
     * @param bDate
     * @param eDate
     * @param dateType
     * @param depart
     * @param giveMode
     * @param fromflag
     * @return
     */
    @RpcService(timeout = 600000)
    @Override
    public List<Map> findRecipeOrdersByInfoForExcel(Integer organId, List<Integer> organIds, Integer status, Integer doctor, String patientName, Date bDate,
                                               Date eDate, Integer dateType, Integer depart, Integer giveMode,
                                               Integer fromflag,Integer recipeId){
        LOGGER.info("findRecipeOrdersByInfoForExcel查询处方订单导出信息入参:{},{},{},{},{},{},{},{},{},{},{},{}",organId, organIds, status, doctor, patientName, bDate, eDate, dateType, depart, giveMode, fromflag, recipeId);
        IRecipeService recipeService = RecipeAPI.getService(IRecipeService.class);
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        List<Map> recipeMap = recipeDAO.findRecipesByInfoForExcel(organId, status, doctor, patientName, bDate, eDate, dateType, depart, organIds, giveMode, fromflag, recipeId);

        //组装数据准备
        Object nowRecipeId;
        Object clinicOrgan;
        Object address4 = null;
        Integer nowRecipedId;
        Integer clinicOrganId;
        RecipeOrder order;
        SaleDrugList saleDrugList;
        OrganDrugList organDrugList;
        List<OrganDrugList> organDrugLists;
        List<RecipeDetailBean> recipeDetails;
        //List<Map<String, Object>> details;
        //Map<String, Object> recipeDetailMap;
        List<Map> newRecipeMap = new ArrayList<>();
        Map<String, Object> recipeMsgMap;
        RecipeOrderDAO recipeOrderDAO = DAOFactory.getDAO(RecipeOrderDAO.class);
        SaleDrugListDAO saleDrugListDAO = DAOFactory.getDAO(SaleDrugListDAO.class);
        OrganDrugListDAO organDrugListDAO = DAOFactory.getDAO(OrganDrugListDAO.class);
        CommonRemoteService commonRemoteService = AppContextHolder.getBean("commonRemoteService", CommonRemoteService.class);

        //组装处方相关联的数据

        for(Map<String, Object> recipeMsg: recipeMap){
            nowRecipeId = recipeMsg.get("recipeId");
            clinicOrgan = recipeMsg.get("clinicOrgan");

            if(null != nowRecipeId){
                try {
                    nowRecipedId = Integer.parseInt(nowRecipeId.toString());
                    clinicOrganId = Integer.parseInt(clinicOrgan.toString());
                    //订单数据
                    LOGGER.info("findRecipeOrdersByInfoForExcel查询订单信息:RecipeId{}", nowRecipedId);
                    order = recipeOrderDAO.getOrderByRecipeId(nowRecipedId);
                    LOGGER.info("findRecipeOrdersByInfoForExcel查询订单信息,{}", JSONUtils.toString(order));
                    //处方对应药品详情信息
                    recipeDetails = recipeService.findRecipeDetailsByRecipeId(nowRecipedId);
                    if(null != recipeDetails && 0 < recipeDetails.size()){
                        //details = new ArrayList<>();
                        for (RecipeDetailBean recipeDetailBean : recipeDetails){
                            //recipeDetailMap = new HashMap();
                            //药品名称
                            recipeMsgMap = new HashMap();
                            recipeMsgMap.putAll(recipeMsg);
                            recipeMsgMap.put("detailDrugName", recipeDetailBean.getDrugName());
                            //规格
                            recipeMsgMap.put("detailDrugSpec", recipeDetailBean.getDrugSpec());
                            //单位
                            recipeMsgMap.put("detailDrugUnit", recipeDetailBean.getDrugUnit());
                            //价格
                            recipeMsgMap.put("detailDrugPrice", recipeDetailBean.getSalePrice());
                            //date 20200225 修改药品查询信息
                            //判断处方详情中药品信息存在去新的值(如果为空说明)
                            if(null != recipeDetailBean.getProducer()){
                                LOGGER.info("findRecipeOrdersByInfoForExcel当前处方关联的药品数据是新数据{}", nowRecipeId);
                                //说明是新签名后添加的数据
                                //批号
                                recipeMsgMap.put("detailDruglicenseNumber", recipeDetailBean.getLicenseNumber());
                                //生产厂家
                                recipeMsgMap.put("detailDrugProducer", recipeDetailBean.getProducer());
                            }else{
                                //说明是老数据
                                LOGGER.info("findRecipeOrdersByInfoForExcel当前处方关联的药品数据是旧数据{}", nowRecipeId);
                                //处方的药品关联信息
                                if (null != recipeDetailBean.getDrugId() && null != clinicOrganId && null != recipeDetailBean.getOrganDrugCode()){

                                    LOGGER.info("findRecipeOrdersByInfoForExcel查询处方机构药品信息:DrugId{},OrganId{},OrganDrugCode{}", recipeDetailBean.getDrugId(), clinicOrganId, recipeDetailBean.getOrganDrugCode());
                                    organDrugLists = organDrugListDAO.findByOrganIdAndDrugIdAndOrganDrugCode(clinicOrganId, recipeDetailBean.getDrugId(), recipeDetailBean.getOrganDrugCode());
                                    LOGGER.info("findRecipeOrdersByInfoForExcel查询处方机构药品信息,{},长度:{}", JSONUtils.toString(organDrugLists));
                                    if(null == organDrugLists || 0 == organDrugLists.size()){
                                        LOGGER.warn("当前处方药品详情关联的机构药品信息不存在DrugId:{},OrganId:{}", recipeDetailBean.getDrugId(), clinicOrganId);
                                    }else{
                                        organDrugList = organDrugLists.get(0);
                                        LOGGER.info("findRecipeOrdersByInfoForExcel查询处方机构药品单个信息:DrugId{},OrganId{},OrganDrugCode{}", JSONUtils.toString(organDrugList));
                                        //机构药品信息存在
                                        //批号
                                        recipeMsgMap.put("detailDruglicenseNumber", organDrugList.getLicenseNumber());
                                        //生产厂家
                                        recipeMsgMap.put("detailDrugProducer", organDrugList.getProducer());

                                    }
                                }

                            }
                            //将药企药品价格更新上去以及药企的药品code
                            if(null != order){
                                LOGGER.info("findRecipeOrdersByInfoForExcel查询处方配送药品信息:DrugId{},OrganId{}", recipeDetailBean.getDrugId(), order.getEnterpriseId());
                                if(null != order.getEnterpriseId()){

                                    saleDrugList = saleDrugListDAO.getByDrugIdAndOrganId(recipeDetailBean.getDrugId(), order.getEnterpriseId());
                                    LOGGER.info("findRecipeOrdersByInfoForExcel查询处方配送药品信息,{}", JSONUtils.toString(saleDrugList));
                                    if(null != saleDrugList && null != saleDrugList.getPrice()){
                                        //价格
                                        //有订单，判断订单对应的药品是否是药企的药品价格
                                        recipeMsgMap.put("detailDrugPrice", saleDrugList.getPrice());
                                    }
                                    if(null != saleDrugList){
                                        //药企药品编码
                                        recipeMsgMap.put("saleDrugCode", saleDrugList.getOrganDrugCode());
                                    }
                                }
                            }


                            //每次剂量
                            recipeMsgMap.put("detailUseDose", recipeDetailBean.getUseDose());
                            //剂量单位
                            recipeMsgMap.put("detailUseDoseUnit", recipeDetailBean.getUseDoseUnit());
                            //用法
                            if(StringUtils.isNotEmpty(recipeDetailBean.getUsePathways())){
                                recipeMsgMap.put("detailUsePathways", DictionaryController.instance().get("eh.cdr.dictionary.UsePathways").getText(recipeDetailBean.getUsePathways()));
                            }
                            //用药频度
                            if(StringUtils.isNotEmpty(recipeDetailBean.getUsingRate())){
                                recipeMsgMap.put("detailUsingRate", DictionaryController.instance().get("eh.cdr.dictionary.UsingRate").getText(recipeDetailBean.getUsingRate()));
                            }
                            //数量
                            recipeMsgMap.put("detailTotalDose", recipeDetailBean.getUseTotalDose());


                            //details.add(recipeDetailMap);
                            recipeAndOrderMsg(address4, order, commonRemoteService, recipeMsgMap);
                            newRecipeMap.add(recipeMsgMap);
                        }
                        //recipeMsg.put("details", details);
                    }else{
                        recipeMsgMap = new HashMap();
                        recipeMsgMap.putAll(recipeMsg);
                        recipeAndOrderMsg(address4, order, commonRemoteService, recipeMsgMap);
                        newRecipeMap.add(recipeMsgMap);
                    }


                } catch (Exception e) {
                    LOGGER.error("查询关联信息异常{}，对应的处方id{}", e, nowRecipeId);
                    e.printStackTrace();
                    throw new DAOException("查询处方信息异常！");
                }
            }
        }
        LOGGER.info("findRecipeOrdersByInfoForExcel查询处方订单导出信息结果:{}", newRecipeMap);
        return newRecipeMap;
    }

    private void recipeAndOrderMsg(Object address4, RecipeOrder order, CommonRemoteService commonRemoteService, Map<String, Object> recipeMsg) throws ControllerException {
        //地址 加非空校验
        address4 = recipeMsg.get("address4");
        DrugsEnterpriseDAO drugsEnterpriseDAO = DAOFactory.getDAO(DrugsEnterpriseDAO.class);
        if (null != address4 && StringUtils.isNotEmpty(address4.toString())) {
            recipeMsg.put("completeAddress", address4.toString());
        } else {
            recipeMsg.put("completeAddress", commonRemoteService.getCompleteAddress(order));
        }
        if(null != order){
            //下单时间
            recipeMsg.put("orderTime", order.getCreateTime());
            //配送费
            recipeMsg.put("expressFee", order.getExpressFee());
            //订单号
            recipeMsg.put("orderCode", order.getOrderCode());
            //订单状态
            if(null != order.getStatus()) {
                recipeMsg.put("orderStatus", DictionaryController.instance().get("eh.cdr.dictionary.RecipeOrderStatus").getText(order.getStatus()));
            }
            //支付金额
            recipeMsg.put("payMoney", order.getActualPrice());
            recipeMsg.put("totalMoney", order.getTotalFee());
            //date 20200303
            //添加药企信息和期望配送时间
            if(null != order.getEnterpriseId()){
                //匹配上药企，获取药企名
                //DrugsEnterpriseDAO drugsEnterpriseDAO = DAOFactory.getDAO(DrugsEnterpriseDAO.class);
                DrugsEnterprise enterprise = drugsEnterpriseDAO.getById(order.getEnterpriseId());
                if(null != enterprise && null != enterprise.getName()){
                    LOGGER.info("findRecipeOrdersByInfoForExcel 当前处方{}关联上药企:{}", order.getRecipeIdList(), JSONUtils.toString(enterprise));
                    recipeMsg.put("enterpriseName", enterprise.getName());
                }else{
                    LOGGER.warn("findRecipeOrdersByInfoForExcel 当前处方{}关联的药企id:{}信息不全", order.getRecipeIdList(), order.getEnterpriseId());
                }

            }
            //date 20200303
            //添加期望配送时间
            if(StringUtils.isNotEmpty(order.getExpectSendDate()) && StringUtils.isNotEmpty(order.getExpectSendTime())){
                recipeMsg.put("expectSendDate", order.getExpectSendDate() + " " + order.getExpectSendTime());
            }
            //date 20200305
            //添加支付状态
            if(null != order.getPayFlag()){
                LOGGER.info("findRecipeOrdersByInfoForExcel 当前处方{}的订单支付状态{}", order.getRecipeIdList(), order.getPayFlag());
                recipeMsg.put("payStatusText", RecipePayTextEnum.getByPayFlag(order.getPayFlag()).getPayText());
            }else{
                recipeMsg.put("payStatusText", RecipePayTextEnum.Default.getPayText());
            }
            recipeMsg.put("payTime", order.getPayTime());
            recipeMsg.put("tradeNo", order.getTradeNo());

        }else{
            //没有订单说明没有支付
            recipeMsg.put("payStatusText", RecipePayTextEnum.Default.getPayText());
        }
    }

    @RpcService
    @Override
    public HashMap<Integer, Long> getCountGroupByOrgan(){
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        return recipeDAO.getCountGroupByOrgan();
    }


    @RpcService
    @Override
    public HashMap<Integer, Long> getRecipeRequestCountGroupByDoctor(){
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        return recipeDAO.getRecipeRequestCountGroupByDoctor();
    }

    @Override
    public List<RecipeBean> findAllReadyAuditRecipe() {
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        List<Recipe> recipes = recipeDAO.findAllReadyAuditRecipe();
        return ObjectCopyUtils.convert(recipes, RecipeBean.class);
    }

    @Override
    public List<RecipeDetailBean> findRecipeDetailsByRecipeId(Integer recipeId) {
        RecipeDetailDAO recipeDetailDAO = DAOFactory.getDAO(RecipeDetailDAO.class);
        List<Recipedetail> recipedetails = recipeDetailDAO.findByRecipeId(recipeId);
        return ObjectCopyUtils.convert(recipedetails, RecipeDetailBean.class);
    }

    @Override
    public RecipeExtendBean findRecipeExtendByRecipeId(Integer recipeId) {
        RecipeExtendDAO RecipeExtendDAO = DAOFactory.getDAO(RecipeExtendDAO.class);
        RecipeExtend recipeExtend = RecipeExtendDAO.getByRecipeId(recipeId);
        return ObjectCopyUtils.convert(recipeExtend, RecipeExtendBean.class);
    }

    @Override
    public List<Integer> findReadyAuditRecipeIdsByOrganIds(List<Integer> organIds) {
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        return recipeDAO.findReadyAuditRecipeIdsByOrganIds(organIds);
    }

    @Override
    public List<String> findSignFileIdByPatientId(String patientId) {
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        return recipeDAO.findSignFileIdByPatientId(patientId);
    }

    @Override
    public List<Integer> findDoctorIdByHistoryRecipe() {
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        return recipeDAO.findDoctorIdByHistoryRecipe();
    }

    @Override
    public RecipeBean getRecipeByOrderCode(String orderCode) {
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        List<Recipe> recipes = recipeDAO.findRecipeListByOrderCode(orderCode);
        if (recipes != null && recipes.size() > 0 ){
            Recipe recipe = recipes.get(0);
            return ObjectCopyUtils.convert(recipe, RecipeBean.class);
        }
        return null;
    }

    @Override
    @RpcService
    public Map<String,Object> noticePlatRecipeFlowInfo(NoticePlatRecipeFlowInfoDTO req) {
        TmdyfRemoteService service = ApplicationUtils.getRecipeService(TmdyfRemoteService.class);
        LOGGER.info("noticePlatRecipeFlowInfo req={}",JSONUtils.toString(req));
        Map<String,Object> map = Maps.newHashMap();
        if (req != null && StringUtils.isNotEmpty(req.getPutOnRecordID())&& StringUtils.isNotEmpty(req.getRecipeID())){
            try {
                DrugEnterpriseResult result = service.updateMedicalInsuranceRecord(req.getRecipeID(), req.getPutOnRecordID());
                if (StringUtils.isNotEmpty(result.getMsg())){
                    map.put("msg",result.getMsg());
                }
                LOGGER.info("noticePlatRecipeFlowInfo res={}",JSONUtils.toString(result));
            }catch (Exception e){
                LOGGER.error("noticePlatRecipeFlowInfo error.",e);
                map.put("msg","处理异常");
            }
        }
        return map;

    }

    @Override
    @RpcService
    public void noticePlatRecipeMedicalInsuranceInfo(NoticePlatRecipeMedicalInfoDTO req) {
        LOGGER.info("noticePlatRecipeMedicalInsuranceInfo req={}",JSONUtils.toString(req));
        if (null == req) {
            return;
        }
        //上传状态
        String uploadStatus = req.getUploadStatus();

        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        Recipe dbRecipe = recipeDAO.getByRecipeCode(req.getRecipeCode());
        if (null != dbRecipe) {
            //默认 医保上传确认中
            Integer status = RecipeStatusConstant.CHECKING_MEDICAL_INSURANCE;
            String memo = "";
            if ("1".equals(uploadStatus)){
                //上传成功
                if (RecipeStatusConstant.READY_CHECK_YS != dbRecipe.getStatus()){
                    status = RecipeStatusConstant.READY_CHECK_YS;
                    memo = "His医保信息上传成功";
                }
                //保存医保返回数据
                saveMedicalInfoForRecipe(req,dbRecipe.getRecipeId());
            }else {
                //上传失败
                //失败原因
                String failureInfo = req.getFailureInfo();
                status = RecipeStatusConstant.RECIPE_MEDICAL_FAIL;
                memo = StringUtils.isEmpty(failureInfo)?"His医保信息上传失败":"His医保信息上传失败,原因:"+failureInfo;
            }
            recipeDAO.updateRecipeInfoByRecipeId(dbRecipe.getRecipeId(), status, null);
            //日志记录
            RecipeLogService.saveRecipeLog(dbRecipe.getRecipeId(), dbRecipe.getStatus(), status, memo);
        }
    }
    private void saveMedicalInfoForRecipe(NoticePlatRecipeMedicalInfoDTO req, Integer recipeId) {
        //医院机构编码
        String hospOrgCodeFromMedical = req.getHospOrgCode();
        //参保地统筹区
        String insuredArea = req.getInsuredArea();
        //医保结算请求串
        String medicalSettleData = req.getMedicalSettleData();
        RecipeExtendDAO recipeExtendDAO = DAOFactory.getDAO(RecipeExtendDAO.class);
        Map<String,String> updateMap = Maps.newHashMap();
        updateMap.put("hospOrgCodeFromMedical",hospOrgCodeFromMedical);
        updateMap.put("insuredArea",insuredArea);
        updateMap.put("medicalSettleData",medicalSettleData);
        recipeExtendDAO.updateRecipeExInfoByRecipeId(recipeId,updateMap);
    }


    /**
     * 获取处方类型的参数接口对像
     *  区别 中药、西药、膏方
     * @param paramMapType
     * @param recipe
     * @param details
     * @param fileName
     * @return
     */
    @Override
    @RpcService
    public Map<String, Object> createRecipeParamMapForPDF(Integer paramMapType, RecipeBean recipe, List<RecipeDetailBean> details, String fileName){
        LOGGER.info("createParamMapForChineseMedicine start in  paramMapType={} recipe={} details={} fileName={}"
                ,paramMapType,JSONObject.toJSONString(recipe),JSONObject.toJSONString(details),fileName);

        Map<String, Object> map;
        List<Recipedetail> recipeDetails = new ArrayList<>();
        for (RecipeDetailBean recipeDetailBean : details){
            recipeDetails.add(getBean(recipeDetailBean,Recipedetail.class));
        }
        //根据处方类型选择生成参数
        if (RecipeUtil.isTcmType(recipe.getRecipeType())) {
            //处方类型-中药 或 膏方
            map = RecipeServiceSub.createParamMapForChineseMedicine(getBean(recipe, Recipe.class), recipeDetails, fileName);
        } else {
            //处方类型-其他类型
            map = RecipeServiceSub.createParamMap(getBean(recipe, Recipe.class), recipeDetails, fileName);

        }
        return map;
    }

    @RpcService
    @Override
    public Boolean updateRecipeInfoByRecipeId(int recipeId, final Map<String,Object> changeAttr){
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        try {
            recipeDAO.updateRecipeInfoByRecipeId(recipeId, changeAttr);
            return true;
        }catch (Exception e){
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public Map<String, Object> getHtml5LinkInfo(PatientInfoDTO patient, RecipeBean recipeBean, List<RecipeDetailBean> recipeDetails, Integer reqType) {
        WinningMedicationGuideService winningMedicationGuideService = ApplicationUtils.getRecipeService(WinningMedicationGuideService.class);
        recipe.medicationguide.bean.PatientInfoDTO patientInfoDTO = ObjectCopyUtils.convert(patient,recipe.medicationguide.bean.PatientInfoDTO.class);
        Map<String,Object> resultMap = winningMedicationGuideService.getHtml5LinkInfo(patientInfoDTO,recipeBean,recipeDetails,reqType);
        return resultMap;
    }

    @Override
    public Map<String, String> getEnterpriseCodeByRecipeId(Integer orderId) {
        Map<String, String> map = new HashMap<String, String>();
        RecipeOrderDAO recipeOrderDAO = DAOFactory.getDAO(RecipeOrderDAO.class);
        RecipeOrder recipeOrder = recipeOrderDAO.get(orderId);
        if(recipeOrder != null){
            map.put("orderType", recipeOrder.getOrderType() == null ? null :recipeOrder.getOrderType() + "");
        } else {
            LOGGER.info("getEnterpriseCodeByRecipeId 获取订单为null orderId = {}",orderId);
        }
        Integer depId = recipeOrder.getEnterpriseId();
        DrugsEnterpriseDAO drugsEnterpriseDAO = DAOFactory.getDAO(DrugsEnterpriseDAO.class);
        if (depId != null) {
            DrugsEnterprise drugsEnterprise = drugsEnterpriseDAO.getById(depId);
            map.put("enterpriseCode", drugsEnterprise.getEnterpriseCode());
        }
        return map;
    }

    @Override
    public Boolean canRequestConsultForRecipe(String mpiId, Integer depId, Integer organId) {
        LOGGER.info("canRequestConsultForRecipe organId={},mpiId={},depId={}",organId,mpiId,depId);
        //先查3天内未处理的线上处方-平台
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        //设置查询时间段
        String endDt = DateConversion.getDateFormatter(new Date(), DateConversion.DEFAULT_DATE_TIME);
        String startDt = DateConversion.getDateFormatter(DateConversion.getDateTimeDaysAgo(3),DateConversion.DEFAULT_DATE_TIME);
        //前置没考虑
        List<Recipe> recipeList = recipeDAO.findRecipeListByDeptAndPatient(depId, mpiId, startDt,endDt);
        if (CollectionUtils.isEmpty(recipeList)){
            //再查3天内线上未缴费的处方-到院取药推送的处方-his
            PatientService patientService = ApplicationUtils.getBasicService(PatientService.class);
            PatientDTO patientDTO = patientService.get(mpiId);
            IRecipeHisService hisService = AppDomainContext.getBean("his.iRecipeHisService", IRecipeHisService.class);
            QueryRecipeRequestTO request = new QueryRecipeRequestTO();
            PatientBaseInfo patientBaseInfo = new PatientBaseInfo();
            patientBaseInfo.setPatientName(patientDTO.getPatientName());
            patientBaseInfo.setCertificate(patientDTO.getCertificate());
            patientBaseInfo.setCertificateType(patientDTO.getCertificateType());
            request.setPatientInfo(patientBaseInfo);
            request.setStartDate(DateConversion.getDateTimeDaysAgo(3));
            request.setEndDate(DateTime.now().toDate());
            request.setOrgan(organId);
            LOGGER.info("canRequestConsultForRecipe-getHosRecipeList req={}", JSONUtils.toString(request));
            QueryRecipeResponseTO response = null;
            try {
                response = hisService.queryRecipeListInfo(request);
            } catch (Exception e) {
                LOGGER.warn("canRequestConsultForRecipe-getHosRecipeList his error. ", e);
            }
            LOGGER.info("canRequestConsultForRecipe-getHosRecipeList res={}", JSONUtils.toString(response));
            if(null == response){
                return true;
            }
            List<RecipeInfoTO> data = response.getData();
            if (CollectionUtils.isEmpty(data)){
                return true;
            }else {
                return false;
            }
        }else {
            return false;
        }
    }

    @RpcService
    @Override
    public void recipeMedicInsurSettle(MedicInsurSettleSuccNoticNgariReqDTO request) {
        LOGGER.info("省医保结算成功通知平台,param = {}", JSONUtils.toString(request));
        if (null == request.getRecipeId()) {
            return;
        }
        try {
            RecipeOrderService recipeOrderService = ApplicationUtils.getRecipeService(RecipeOrderService.class);
            recipeOrderService.recipeMedicInsurSettleUpdateOrder(request);
        } catch (Exception e) {
            LOGGER.info("recipeMedicInsurSettle error", e);
        }
        return;
    }

    @Override
    @RpcService
    public String getRecipeOrderCompleteAddress(RecipeOrderBean orderBean) {
        CommonRemoteService commonRemoteService = AppContextHolder.getBean("commonRemoteService", CommonRemoteService.class);
        return commonRemoteService.getCompleteAddress(getBean(orderBean,RecipeOrder.class));
    }

    @RpcService
    @Override
    public Map<String, Object> noticePlatRecipeAuditResult(NoticeNgariAuditResDTO req) {
        LOGGER.info("noticePlatRecipeAuditResult，req = {}", JSONUtils.toString(req));
        Map<String, Object> resMap = Maps.newHashMap();
        try {
            RecipeCheckService recipeService = ApplicationUtils.getRecipeService(RecipeCheckService.class);
            RecipeDAO dao = DAOFactory.getDAO(RecipeDAO.class);
            Recipe recipe = dao.getByRecipeCodeAndClinicOrgan(req.getRecipeCode(), req.getOrganId());
            if (recipe == null){
                resMap.put("msg","查询不到处方信息");
            }
            Map<String, Object> paramMap = Maps.newHashMap();
            paramMap.put("recipeId",recipe.getRecipeId());
            //1:审核通过 0-通过失败
            paramMap.put("result",req.getAuditResult());
            //审核机构
            paramMap.put("checkOrgan",req.getOrganId());
            //审核药师工号
            paramMap.put("auditDoctorCode",req.getAuditDoctorCode());
            //审核药师姓名
            paramMap.put("auditDoctorName",req.getAuditDoctorName());
            //审核不通过原因备注
            paramMap.put("failMemo",req.getMemo());
            //审核时间
            paramMap.put("auditTime",req.getAuditTime());
            Map<String, Object> result = recipeService.saveCheckResult(paramMap);
            //错误消息返回
            if (result!=null && result.get("msg") != null){
                resMap.put("msg",result.get("msg"));
            }
            LOGGER.info("noticePlatRecipeAuditResult，res = {}", JSONUtils.toString(result));
        }catch (Exception e){
            resMap.put("msg",e.getMessage());
            LOGGER.error("noticePlatRecipeAuditResult，error= {}", e);
        }
        return resMap;
    }

    @Override
    public long getCountByOrganAndDeptIds(Integer organId, List<Integer> deptIds) {
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        return recipeDAO.getCountByOrganAndDeptIds(organId, deptIds);
    }

    @RpcService
    @Override
    public List<Object[]> countRecipeIncomeGroupByDeptId(Date startDate, Date endDate, Integer organId) {
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        return recipeDAO.countRecipeIncomeGroupByDeptId(startDate, endDate, organId);
    }

    @Override
    public List<RecipeBean> findByClinicId(Integer consultId) {
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        return recipeDAO.findByClinicId(consultId);
    }

    @Override
    @RpcService
    public BigDecimal getRecipeCostCountByOrganIdAndDepartIds(Integer organId, Date startDate, Date endDate, List<Integer> deptIds) {
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        return recipeDAO.getCostCountByOrganIdAndDepartIds(organId, startDate, endDate, deptIds);
    }

}
