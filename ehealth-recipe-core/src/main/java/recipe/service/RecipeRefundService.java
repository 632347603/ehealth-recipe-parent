package recipe.service;

import com.google.common.collect.Maps;
import com.ngari.base.BaseAPI;
import com.ngari.base.property.service.IConfigurationCenterUtilsService;
import com.ngari.common.mode.HisResponseTO;
import com.ngari.his.visit.mode.ApplicationForRefundVisitReqTO;
import com.ngari.his.visit.mode.CheckForRefundVisitReqTO;
import com.ngari.his.visit.mode.FindRefundRecordReqTO;
import com.ngari.his.visit.mode.FindRefundRecordResponseTO;
import com.ngari.his.visit.service.IVisitService;
import com.ngari.patient.dto.DoctorDTO;
import com.ngari.patient.dto.OrganDTO;
import com.ngari.patient.service.DoctorService;
import com.ngari.patient.service.EmploymentService;
import com.ngari.patient.service.OrganService;
import com.ngari.patient.utils.ObjectCopyUtils;
import com.ngari.recipe.entity.*;
import com.ngari.recipe.recipe.model.RecipeRefundBean;
import com.ngari.recipe.recipe.model.RefundRequestBean;
import ctd.controller.exception.ControllerException;
import ctd.dictionary.DictionaryController;
import com.ngari.recipe.common.RecipePatientRefundVO;
import com.ngari.recipe.entity.Recipe;
import com.ngari.recipe.entity.RecipeOrder;
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
import recipe.ApplicationUtils;
import recipe.constant.DrugEnterpriseConstant;
import recipe.constant.RecipeStatusConstant;
import recipe.dao.*;

import java.text.SimpleDateFormat;
import java.util.*;

import static ctd.persistence.DAOFactory.getDAO;


/**
 * 处方退费
 * company: ngarihealth
 *
 * @author: gaomw
 * @date:20200714
 */
@RpcBean(value = "recipeRefundService", mvc_authentication = false)
public class RecipeRefundService extends RecipeBaseService{

    private static final Logger LOGGER = LoggerFactory.getLogger(RecipeRefundService.class);

    /*
     * @description 向his申请处方退费接口
     * @author gmw
     * @date 2020/7/15
     * @param recipeId 处方序号
     * @param applyReason 申请原因
     * @return 申请序号
     */
    @RpcService(timeout = 60)
    public void applyForRecipeRefund(Integer recipeId, String applyReason) {
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        Recipe recipe = recipeDAO.getByRecipeId(recipeId);
        if(recipe == null || recipe.getOrderCode() == null){
            LOGGER.error("applyForRecipeRefund-未获取到处方单信息. recipeId={}", recipeId.toString());
            throw new DAOException("未获取到处方订单信息！");
        }
        RecipeOrderDAO recipeOrderDAO = DAOFactory.getDAO(RecipeOrderDAO.class);
        RecipeOrder recipeOrder = recipeOrderDAO.getByOrderCode(recipe.getOrderCode());
        if(recipeOrder == null){
            LOGGER.error("applyForRecipeRefund-未获取到处方单信息. recipeId={}", recipeId.toString());
            throw new DAOException("未获取到处方订单信息！");
        }
        ApplicationForRefundVisitReqTO request = new ApplicationForRefundVisitReqTO();
        request.setOrganId(recipe.getClinicOrgan());
        request.setBusNo(recipeOrder.getTradeNo());
        request.setPatientId(recipe.getPatientID());
        request.setPatientName(recipe.getPatientName());
        request.setApplyReason(applyReason);

        IVisitService service = AppContextHolder.getBean("his.visitService", IVisitService.class);

        IConfigurationCenterUtilsService configurationService = ApplicationUtils.getBaseService(IConfigurationCenterUtilsService.class);
        Boolean doctorReviewRefund = (Boolean) configurationService.getConfiguration(recipe.getClinicOrgan(), "doctorReviewRefund");
        if (doctorReviewRefund) {
            //date 20201012 加长了接口过期时间，接口20s会超时
            HisResponseTO<String> hisResult = service.applicationForRefundVisit(request);
            //说明需要医生进行审核，则需要推送给医生，此处兼容上海六院，其他医院前置机需要实现此接口并返回成功但不需要真实对接第三方
            if (hisResult != null && "200".equals(hisResult.getMsgCode())) {
                LOGGER.info("applyForRecipeRefund-处方退费申请成功-his. param={},result={}", JSONUtils.toString(request), JSONUtils.toString(hisResult));
                //退费申请记录保存
                RecipeRefund recipeRefund = new RecipeRefund();
                recipeRefund.setTradeNo(recipeOrder.getTradeNo());
                recipeRefund.setPrice(recipeOrder.getActualPrice());
                recipeRefund.setNode(-1);
                recipeRefund.setApplyNo(hisResult.getData());
                recipeRefund.setReason(applyReason);
                recipeReFundSave(recipe, recipeRefund);
                RecipeMsgService.batchSendMsg(recipeId, RecipeStatusConstant.RECIPE_REFUND_APPLY);
            } else {
                LOGGER.error("applyForRecipeRefund-处方退费申请失败-his. param={},result={}", JSONUtils.toString(request), JSONUtils.toString(hisResult));
                String msg = "";
                if(hisResult != null && hisResult.getMsg() != null){
                    msg = hisResult.getMsg();
                }
                throw new DAOException("处方退费申请失败！" + msg);
            }
        } else {
            //不需要医生审核，则直接推送给第三方
            CheckForRefundVisitReqTO visitRequest = new CheckForRefundVisitReqTO();
            visitRequest.setOrganId(recipe.getClinicOrgan());
            visitRequest.setBusNo(recipeOrder.getTradeNo());
            visitRequest.setPatientId(recipe.getPatientID());
            visitRequest.setPatientName(recipe.getPatientName());
            DoctorService doctorService = ApplicationUtils.getBasicService(DoctorService.class);
            EmploymentService iEmploymentService = ApplicationUtils.getBasicService(EmploymentService.class);
            OrganService organService = ApplicationUtils.getBasicService(OrganService.class);
            OrganDTO organDTO = organService.getByOrganId(recipe.getClinicOrgan());
            DoctorDTO doctorDTO = doctorService.getByDoctorId(recipe.getDoctor());
            if (null != doctorDTO) {
                visitRequest.setChecker(iEmploymentService.getJobNumberByDoctorIdAndOrganIdAndDepartment(doctorDTO.getDoctorId(), recipe.getClinicOrgan(), recipe.getDepart()));
                visitRequest.setCheckerName(doctorDTO.getName());
            }
            visitRequest.setCheckNode("-1");
            visitRequest.setCheckReason(applyReason);
            SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHH:mm:ss");
            visitRequest.setCheckTime(formatter.format(new Date()));
            visitRequest.setHospitalCode(organDTO.getOrganizeCode());
            visitRequest.setRecipeCode(recipe.getRecipeCode());
            visitRequest.setRefundType(getRefundType(recipeOrder));

            HisResponseTO<String> result = service.checkForRefundVisit(visitRequest);
            if (result != null && "200".equals(result.getMsgCode())) {
                LOGGER.info("applyForRecipeRefund-处方退费申请成功-his. param={},result={}", JSONUtils.toString(request), JSONUtils.toString(result));
                //退费审核记录保存
                RecipeRefund recipeRefund = new RecipeRefund();
                recipeRefund.setTradeNo(recipeOrder.getTradeNo());
                recipeRefund.setPrice(recipeOrder.getActualPrice());
                recipeRefund.setNode(-1);
                recipeRefund.setReason(applyReason);
                recipeReFundSave(recipe, recipeRefund);
            } else {
                LOGGER.error("applyForRecipeRefund-处方退费申请失败-his. param={},result={}", JSONUtils.toString(request), JSONUtils.toString(result));
                throw new DAOException("处方退费申请失败！" + result.getMsg());
            }
        }
    }

    /**
     * 处方退款结果回调
     * @param refundRequestBean 请求信息
     */
    @RpcService
    public void refundResultCallBack(RefundRequestBean refundRequestBean){
        LOGGER.info("RecipeRefundService.refundResultCallBack refundRequestBean:{}.", JSONUtils.toString(refundRequestBean));
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        RecipeOrderDAO recipeOrderDAO = DAOFactory.getDAO(RecipeOrderDAO.class);
        if (refundRequestBean != null && StringUtils.isNotEmpty(refundRequestBean.getRecipeCode())) {
            Recipe recipe = recipeDAO.getByHisRecipeCodeAndClinicOrgan(refundRequestBean.getRecipeCode(), refundRequestBean.getOrganId());
            RecipeOrder recipeOrder = recipeOrderDAO.getByOrderCode(recipe.getOrderCode());
            if (refundRequestBean.getRefundFlag()) {
                if (new Integer(1).equals(recipeOrder.getRecipePayWay())) {
                    //表示药品费用在线上支付
                    RecipeService recipeService = ApplicationUtils.getRecipeService(RecipeService.class);
                    recipeService.wxPayRefundForRecipe(4, recipe.getRecipeId(), "");
                }
                //退费申请记录保存
                RecipeRefund recipeRefund = new RecipeRefund();
                recipeRefund.setTradeNo(recipeOrder.getTradeNo());
                recipeRefund.setPrice(recipeOrder.getActualPrice());
                recipeRefund.setNode(5);
                recipeRefund.setStatus(1);
                recipeReFundSave(recipe, recipeRefund);
                //审核通过
                RecipeMsgService.batchSendMsg(recipe.getRecipeId(), RecipeStatusConstant.RECIPE_REFUND_HIS_OR_PHARMACEUTICAL_AUDIT_SUCCESS);
            } else {
                //退费申请记录保存
                RecipeRefund recipeRefund = new RecipeRefund();
                recipeRefund.setTradeNo(recipeOrder.getTradeNo());
                recipeRefund.setPrice(recipeOrder.getActualPrice());
                recipeRefund.setNode(5);
                recipeRefund.setStatus(2);
                recipeRefund.setReason(refundRequestBean.getRemark());
                recipeReFundSave(recipe, recipeRefund);
                //药企审核不通过
                RecipeMsgService.batchSendMsg(recipe.getRecipeId(), RecipeStatusConstant.RECIPE_REFUND_HIS_OR_PHARMACEUTICAL_AUDIT_FAIL);
            }
        }
    }

    /*
     * @description 向his提交处方退费医生审核结果接口
     * @author gmw
     * @date 2020/7/15
     * @param recipeId 处方序号
     * @param checkStatus 审核状态
     * @param checkReason 审核原因
     * @return 申请序号
     */
    @RpcService
    public void checkForRecipeRefund(Integer recipeId, String checkStatus, String checkReason) {
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        Recipe recipe = recipeDAO.getByRecipeId(recipeId);
        if(recipe == null){
            LOGGER.error("checkForRecipeRefund-未获取到处方单信息. recipeId={}", recipeId.toString());
            throw new DAOException("未获取到处方单信息！");
        }

        RecipeRefundDAO recipeRefundDao = DAOFactory.getDAO(RecipeRefundDAO.class);
        List<RecipeRefund> list = recipeRefundDao.findRefundListByRecipeId(recipeId);
        if(list == null && list.size() == 0){
            LOGGER.error("checkForRecipeRefund-未获取到处方退费信息. recipeId={}", recipeId);
            throw new DAOException("未获取到处方退费信息！");
        }

        CheckForRefundVisitReqTO request = new CheckForRefundVisitReqTO();
        request.setOrganId(recipe.getClinicOrgan());
        request.setApplyNoHis(list.get(0).getApplyNo());
        request.setBusNo(list.get(0).getTradeNo());
        request.setPatientId(recipe.getPatientID());
        request.setPatientName(recipe.getPatientName());
        DoctorService doctorService = ApplicationUtils.getBasicService(DoctorService.class);
        EmploymentService iEmploymentService = ApplicationUtils.getBasicService(EmploymentService.class);
        DoctorDTO doctorDTO = doctorService.getByDoctorId(recipe.getDoctor());
        if (null != doctorDTO) {
            request.setChecker(iEmploymentService.getJobNumberByDoctorIdAndOrganIdAndDepartment(doctorDTO.getDoctorId(), recipe.getClinicOrgan(), recipe.getDepart()));
            request.setCheckerName(doctorDTO.getName());
        }
        request.setCheckStatus(checkStatus);
        request.setCheckNode("0");
        request.setCheckReason(checkReason);
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHH:mm:ss");
        request.setCheckTime(formatter.format(new Date()));

        IVisitService service = AppContextHolder.getBean("his.visitService", IVisitService.class);
        HisResponseTO<String> hisResult = service.checkForRefundVisit(request);
        if (hisResult != null && "200".equals(hisResult.getMsgCode())) {
            LOGGER.info("checkForRecipeRefund-处方退费审核成功-his. param={},result={}", JSONUtils.toString(request), JSONUtils.toString(hisResult));
            //退费审核记录保存
            RecipeRefund recipeRefund = new RecipeRefund();
            recipeRefund.setNode(0);
            recipeRefund.setStatus(Integer.valueOf(checkStatus));
            recipeRefund.setReason(checkReason);
            recipeRefund.setTradeNo(list.get(0).getTradeNo());
            recipeRefund.setPrice(list.get(0).getPrice());
            recipeRefund.setApplyNo(hisResult.getData());
            recipeReFundSave(recipe, recipeRefund);
            if(2 == Integer.valueOf(checkStatus)){
                RecipeMsgService.batchSendMsg(recipeId, RecipeStatusConstant.RECIPE_REFUND_AUDIT_FAIL);
            }
        } else {
            LOGGER.error("checkForRecipeRefund-处方退费审核失败-his. param={},result={}", JSONUtils.toString(request), JSONUtils.toString(hisResult));
            throw new DAOException("处方退费审核失败！" + hisResult.getMsg());
        }

    }


    /*
     * @description 退费记录保存
     * @author gmw
     * @date 2020/7/15
     * @param recipe 处方
     * @param recipeRefund 退费
     */
    @RpcService
    public void recipeReFundSave(Recipe recipe, RecipeRefund recipeRefund) {
        RecipeRefundDAO recipeRefundDao = DAOFactory.getDAO(RecipeRefundDAO.class);
        recipeRefund.setBusId(recipe.getRecipeId());
        recipeRefund.setOrganId(recipe.getClinicOrgan());
        recipeRefund.setMpiid(recipe.getMpiid());
        recipeRefund.setPatientName(recipe.getPatientName());
        recipeRefund.setDoctorId(recipe.getDoctor());
        String memo = null;
        try {
            memo = DictionaryController.instance().get("eh.cdr.dictionary.RecipeRefundNode").getText(recipeRefund.getNode()) +
                DictionaryController.instance().get("eh.cdr.dictionary.RecipeRefundCheckStatus").getText(recipeRefund.getStatus());
        } catch (ControllerException e) {
            LOGGER.error("recipeReFundSave-未获取到处方单信息. recipeId={}, node={}, recipeRefund={}", recipe, JSONUtils.toString(recipeRefund));
            throw new DAOException("退费相关字典获取失败");
        }
        recipeRefund.setMemo(memo);
        if(recipeRefund.getNode() == -1){
            recipeRefund.setStatus(0);
            recipeRefund.setMemo("患者发起退费申请");
        }
        recipeRefund.setNode(recipeRefund.getNode());
        recipeRefund.setStatus(recipeRefund.getStatus());
        recipeRefund.setApplyTime(new Date());
        recipeRefund.setCheckTime(new Date());
        //保存记录
        recipeRefundDao.saveRefund(recipeRefund);

    }

    /*
     * @description 向his查询退费记录
     * @author gmw
     * @date 2020/7/15
     * @param recipeId 处方序号
     * @return 申请序号
     */
    @RpcService
    public FindRefundRecordResponseTO findRefundRecordfromHis(Integer recipeId, String applyNo) {
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        RecipeOrderDAO recipeOrderDAO = DAOFactory.getDAO(RecipeOrderDAO.class);
        Recipe recipe = recipeDAO.getByRecipeId(recipeId);
        if(recipe == null){
            LOGGER.error("findRefundRecordfromHis-未获取到处方单信息. recipeId={}", recipeId.toString());
            throw new DAOException("未获取到处方单信息！");
        }
        RecipeOrder recipeOrder = recipeOrderDAO.getRecipeOrderByRecipeId(recipeId);
        FindRefundRecordReqTO request = new FindRefundRecordReqTO();
        request.setOrganId(recipe.getClinicOrgan());
        request.setBusNo(applyNo);
        request.setPatientId(recipe.getPatientID());
        request.setPatientName(recipe.getPatientName());
        request.setRefundType(getRefundType(recipeOrder));

        IVisitService service = AppContextHolder.getBean("his.visitService", IVisitService.class);
        HisResponseTO<FindRefundRecordResponseTO> hisResult = service.findRefundRecord(request);
        if (hisResult != null && "200".equals(hisResult.getMsgCode())) {
            LOGGER.info("findRefundRecordfromHis-获取his退费记录成功-his. param={},result={}", JSONUtils.toString(request), JSONUtils.toString(hisResult));
            return hisResult.getData();
        } else {
            LOGGER.error("findRefundRecordfromHis-获取his退费记录失败-his. param={},result={}", JSONUtils.toString(request), JSONUtils.toString(hisResult));
            String msg = "";
            if(hisResult != null && hisResult.getMsg() != null){
                msg = hisResult.getMsg();
            }
            throw new DAOException("获取医院退费记录失败！" + msg);
        }
    }

    /*
     * @description 查询退费进度
     * @author gmw
     * @date 2020/7/15
     * @param recipeId 处方序号
     * @return 申请序号
     */
    @RpcService
    public List<RecipeRefundBean> findRecipeReFundRate(Integer recipeId) {
        RecipeRefundDAO recipeRefundDao = DAOFactory.getDAO(RecipeRefundDAO.class);
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        List<RecipeRefund> list = recipeRefundDao.findRefundListByRecipeId(recipeId);
        if(list == null || list.size() == 0){
            LOGGER.error("findRecipeReFundRate-未获取到处方退费信息. recipeId={}", recipeId);
            throw new DAOException("未获取到处方退费信息！");
        }
        RecipeRefund applyRefund = recipeRefundDao.getRecipeRefundByRecipeIdAndNode(recipeId, -1);
        List<RecipeRefundBean> result = new ArrayList<>();
        //医生审核后还需要获取医院his的审核状态(医生已审核且通过、还未退费)
        RecipeRefund refundTemp = list.get(0);
        if(refundTemp.getNode() >= 0 && refundTemp.getNode() != 9 && !(refundTemp.getNode() == 0 && refundTemp.getStatus() == 2)){
            RecipeRefund recipeRefund = null;
            try {
                FindRefundRecordResponseTO record = findRefundRecordfromHis(recipeId, null != applyRefund ? applyRefund.getApplyNo() : null);
                //当his的审核记录发生变更时才做记录
                if(null != record && !(refundTemp.getNode().equals(Integer.valueOf(record.getCheckNode()))
                                        && refundTemp.getStatus().equals(Integer.valueOf(record.getCheckStatus())))){
                    recipeRefund = ObjectCopyUtils.convert(refundTemp, RecipeRefund.class);
                    recipeRefund.setNode(Integer.valueOf(record.getCheckNode()));
                    recipeRefund.setStatus(Integer.valueOf(record.getCheckStatus()));
                    recipeRefund.setReason(record.getReason());
                    String memo = DictionaryController.instance().get("eh.cdr.dictionary.RecipeRefundNode").getText(record.getCheckNode()) +
                        DictionaryController.instance().get("eh.cdr.dictionary.RecipeRefundCheckStatus").getText(record.getCheckStatus());
                    recipeRefund.setMemo(memo);
                    recipeRefund.setApplyTime(new Date());
                    recipeRefund.setCheckTime(new Date());
                    //保存记录
                    recipeRefundDao.saveRefund(recipeRefund);
                    //date 20200717
                    //添加推送逻辑
//                    if(9 == Integer.valueOf(record.getCheckNode())){
//                        if(1 == Integer.valueOf(record.getCheckStatus())){
//                            RecipeMsgService.batchSendMsg(recipeId, RecipeStatusConstant.RECIPE_REFUND_SUCC);
//                            //修改处方单状态
//                            recipeDAO.updateRecipeInfoByRecipeId(recipeId, RecipeStatusConstant.REVOKE, null);
//                        }
//                        if(2 == Integer.valueOf(record.getCheckStatus())){
//                            RecipeMsgService.batchSendMsg(recipeId, RecipeStatusConstant.RECIPE_REFUND_FAIL);
//
//                        }
//                    }
                    //将最新记录返回到前端
                    result.add(ObjectCopyUtils.convert(recipeRefund, RecipeRefundBean.class));
                }
            } catch (Exception e) {
                throw new DAOException(e);
            }
        }

        for(int i=0; i<list.size(); i++){

            RecipeRefundBean recipeRefundBean2 =ObjectCopyUtils.convert(list.get(i), RecipeRefundBean.class);
            //退费申请多加一天等待审核的数据，并且去掉理由
            if(list.get(i).getNode().equals(-1)){
                RecipeRefundBean recipeRefundBean = new RecipeRefundBean();
                recipeRefundBean.setBusId(list.get(i).getBusId());
                recipeRefundBean.setMemo("等待审核");
                result.add(recipeRefundBean);
                recipeRefundBean2.setReason(null);
            }
            result.add(recipeRefundBean2);
        }
        return result;

    }

    /*
     * @description 是否展示查看进度按钮
     * @author gmw
     * @date 2020/7/15
     * @param recipeId 处方序号
     * @return 是否展示
     */
    @RpcService
    public boolean refundRateShow(Integer recipeId) {
        RecipeRefundDAO recipeRefundDao = DAOFactory.getDAO(RecipeRefundDAO.class);
        List<RecipeRefund> list = recipeRefundDao.findRefundListByRecipeId(recipeId);
        if(list == null || list.size() == 0){
            return false;
        } else {
            return true;
        }
    }

    @RpcService
    public List<RecipePatientRefundVO> findPatientRefundRecipesByDoctorId(Integer doctorId, Integer refundType, int start, int limit) {
        List<RecipePatientRefundVO> result = new ArrayList<RecipePatientRefundVO>();
        //获取当前医生的退费处方列表，根据当前处方的开方医生审核列表获取当前退费最新的一条记录
        RecipeRefundDAO recipeRefundDAO = getDAO(RecipeRefundDAO.class);
        RecipeDAO recipeDAO = getDAO(RecipeDAO.class);
        RecipeOrderDAO recipeOrderDAO = getDAO(RecipeOrderDAO.class);
        return recipeRefundDAO.findDoctorPatientRefundListByRefundType(doctorId, refundType, start, limit);
    }

    private void initRecipeRefundVo(List<Integer> noteList, Recipe recipe, RecipeOrder recipeOrder, Integer recipeId, RecipePatientRefundVO recipePatientRefundVO) {
        RecipeRefundDAO recipeRefundDAO = getDAO(RecipeRefundDAO.class);
        recipePatientRefundVO.setBusId(recipeId);
        List<RecipeRefund> nodes = recipeRefundDAO.findRefundListByRecipeIdAndNodes(recipeId, noteList);
        for(RecipeRefund recipeRefund : nodes){
            recipePatientRefundVO.setDoctorId(recipeRefund.getDoctorId());
            if(0 == recipeRefund.getNode()){
                recipePatientRefundVO.setDoctorNoPassReason(recipeRefund.getReason());
            }
            recipePatientRefundVO.setPatientMpiid(recipe.getMpiid());
            recipePatientRefundVO.setPatientName(recipe.getPatientName());
            recipePatientRefundVO.setRefundPrice(recipeOrder.getActualPrice());
            if(-1 == recipeRefund.getNode()){
                recipePatientRefundVO.setRefundReason(recipeRefund.getReason());
                recipePatientRefundVO.setRefundDate(recipeRefund.getCheckTime());
            }

        }
        if(CollectionUtils.isNotEmpty(nodes)){
            try {
                recipePatientRefundVO.setRefundStatusMsg(DictionaryController.instance().get("eh.cdr.dictionary.RecipeRefundCheckStatus").getText(nodes.get(0).getStatus()));
            } catch (Exception e) {
                throw new DAOException(e);
            }
        }
        recipePatientRefundVO.setRefundStatus(nodes.get(0).getStatus());
    }

    @RpcService
    public RecipePatientRefundVO getPatientRefundRecipeByRecipeId(Integer busId) {

        RecipeDAO recipeDAO = getDAO(RecipeDAO.class);
        RecipeRefundDAO recipeRefundDAO = getDAO(RecipeRefundDAO.class);
        return recipeRefundDAO.getDoctorPatientRefundByRecipeId(busId);
    }

    //用户提交退费申请给医生
    @RpcService(timeout = 60)
    public Map<String, Object> startRefundRecipeToDoctor(Integer recipeId, String patientRefundReason){
        Map<String, Object> result = Maps.newHashMap();

        try {
            applyForRecipeRefund(recipeId, patientRefundReason);
        } catch (Exception e) {
            throw new DAOException(609,e.getMessage());
        }

        result.put("result", true);
        result.put("code", 200);
        return result;
    }

    //医生端审核患者退费，通过不通过的
    @RpcService
    public Map<String, Object> doctorCheckRefundRecipe(Integer busId, Boolean checkResult, String doctorNoPassReason){
        Map<String, Object> result = Maps.newHashMap();
        try {
            checkForRecipeRefund(busId,checkResult ? "1" : "2",doctorNoPassReason);
        } catch (Exception e) {
            throw new DAOException(609,e.getMessage());
        }
        result.put("result", true);
        result.put("code", 200);
        return result;
    }

    /**
     * 获取退款方式
     * @param recipeOrder 订单信息
     * @return 退款方式
     */
    private Integer getRefundType(RecipeOrder recipeOrder){
        if (recipeOrder == null) {
            throw new DAOException("订单为空");
        }
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        DrugsEnterpriseDAO enterpriseDAO = DAOFactory.getDAO(DrugsEnterpriseDAO.class);
        Recipe recipe = recipeDAO.getByOrderCode(recipeOrder.getOrderCode());
        if (new Integer(1).equals(recipe.getGiveMode()) || new Integer(3).equals(recipe.getGiveMode())) {
            //当处方的购药方式为配送到家和药店取药时
            DrugsEnterprise drugsEnterprise = enterpriseDAO.getById(recipeOrder.getEnterpriseId());
            if (DrugEnterpriseConstant.COMPANY_COMMON_SELF.equals(drugsEnterprise.getCallSys())) {
                //表示为医院自建药企，退费流程应该走院内的退费流程
                return 3;
            } else {
                //表示为第三方真实药企，审核需要走药企的流程
                return 2;
            }
        } else if (new Integer(2).equals(recipe.getGiveMode())) {
            //表示为到院取药
            return 3;
        } else {
            return 1;
        }
    }

    @RpcService
    public List<String> getApp(Integer organId){
        IConfigurationCenterUtilsService configService = BaseAPI.getService(IConfigurationCenterUtilsService.class);
        Object payModeDeploy = configService.getConfiguration(organId, "refundPayModel");
        return new ArrayList<>(Arrays.asList((String[])payModeDeploy));
    }
}
