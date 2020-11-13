package recipe.service;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.ngari.base.BaseAPI;
import com.ngari.base.dto.UsePathwaysDTO;
import com.ngari.base.dto.UsingRateDTO;
import com.ngari.base.hisconfig.service.IHisConfigService;
import com.ngari.base.patient.model.HealthCardBean;
import com.ngari.base.patient.model.PatientBean;
import com.ngari.base.patient.service.IPatientService;
import com.ngari.base.property.service.IConfigurationCenterUtilsService;
import com.ngari.bus.hosrelation.model.HosrelationBean;
import com.ngari.bus.hosrelation.service.IHosrelationService;
import com.ngari.bus.op.service.IUsePathwaysService;
import com.ngari.bus.op.service.IUsingRateService;
import com.ngari.common.mode.HisResponseTO;
import com.ngari.his.base.PatientBaseInfo;
import com.ngari.his.recipe.mode.*;
import com.ngari.his.recipe.service.IRecipeHisService;
import com.ngari.patient.dto.DepartmentDTO;
import com.ngari.patient.dto.OrganDTO;
import com.ngari.patient.dto.PatientDTO;
import com.ngari.patient.service.*;
import com.ngari.patient.utils.ObjectCopyUtils;
import com.ngari.recipe.common.RecipeResultBean;
import com.ngari.recipe.drug.model.UseDoseAndUnitRelationBean;
import com.ngari.recipe.entity.*;
import com.ngari.recipe.hisprescription.model.SyncEinvoiceNumberDTO;
import com.ngari.recipe.recipe.model.*;
import com.ngari.revisit.RevisitAPI;
import com.ngari.revisit.RevisitBean;
import com.ngari.revisit.common.model.RevisitExDTO;
import com.ngari.revisit.common.service.IRevisitExService;
import com.ngari.revisit.common.service.IRevisitService;
import ctd.controller.exception.ControllerException;
import ctd.dictionary.DictionaryController;
import ctd.persistence.DAOFactory;
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
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import recipe.ApplicationUtils;
import recipe.bean.CheckYsInfoBean;
import recipe.bussutil.RecipeUtil;
import recipe.bussutil.UsePathwaysFilter;
import recipe.bussutil.UsingRateFilter;
import recipe.constant.CacheConstant;
import recipe.constant.ErrorCode;
import recipe.constant.RecipeBussConstant;
import recipe.constant.RecipeStatusConstant;
import recipe.dao.*;
import recipe.dao.bean.DrugInfoHisBean;
import recipe.drugsenterprise.AccessDrugEnterpriseService;
import recipe.drugsenterprise.CommonRemoteService;
import recipe.drugsenterprise.RemoteDrugEnterpriseService;
import recipe.hisservice.HisRequestInit;
import recipe.hisservice.RecipeToHisCallbackService;
import recipe.hisservice.RecipeToHisService;
import recipe.purchase.PayModeOnline;
import recipe.purchase.PurchaseEnum;
import recipe.purchase.PurchaseService;
import recipe.service.manager.EmrRecipeManager;
import recipe.thread.CardDataUploadRunable;
import recipe.thread.RecipeBusiThreadPool;
import recipe.util.DateConversion;
import recipe.util.DigestUtil;
import recipe.util.MapValueUtil;
import recipe.util.RedisClient;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static recipe.service.RecipeServiceSub.convertSensitivePatientForRAP;

/**
 * @author yu_yun
 * his接口服务
 */
@RpcBean("recipeHisService")
public class RecipeHisService extends RecipeBaseService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RecipeHisService.class);

    private IPatientService iPatientService = ApplicationUtils.getBaseService(IPatientService.class);
    @Autowired
    private RecipeExtendDAO recipeExtendDAO;
    @Autowired
    private RedisClient redisClient;
    @Autowired
    private PatientService patientService;
    @Autowired
    private IRevisitExService consultExService;
    @Resource
    private OrganDrugListDAO organDrugListDAO;
    @Autowired
    private DrugListDAO drugListDAO;
    @Autowired
    private IRecipeHisService recipeHisService;
    @Autowired
    private IRevisitService consultService;

    @Autowired
    private IUsingRateService usingRateService;
    @Autowired
    private IUsePathwaysService usePathwaysService;

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
            LOGGER.info("skip his!!! recipeId={}", recipeId);
            doHisReturnSuccess(recipe);
            return true;
        }

        Integer sendOrganId = (null == otherOrganId) ? recipe.getClinicOrgan() : otherOrganId;
        if (isHisEnable(sendOrganId)) {
            //推送处方
            try {
                sendRecipe(recipeId, sendOrganId);
            } catch (Exception e) {
                LOGGER.error("recipeSendHis error, recipeId={}", recipeId, e);
            }
        } else {
            result = false;
            LOGGER.error("recipeSendHis 医院HIS未启用[organId:" + sendOrganId + ",recipeId:" + recipeId + "]");
        }
        return result;
    }


    @RpcService
    public void sendRecipe(Integer recipeId, Integer sendOrganId) {
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        Recipe recipe = recipeDAO.getByRecipeId(recipeId);
        RecipeDetailDAO recipeDetailDAO = DAOFactory.getDAO(RecipeDetailDAO.class);
        RecipeToHisService service = AppContextHolder.getBean("recipeToHisService", RecipeToHisService.class);

        List<Recipedetail> details = recipeDetailDAO.findByRecipeId(recipeId);
        PatientBean patientBean = iPatientService.get(recipe.getMpiid());
        HealthCardBean cardBean = null;
        try {
            cardBean = iPatientService.getHealthCard(recipe.getMpiid(), recipe.getClinicOrgan(), "2");

        } catch (Exception e) {
            LOGGER.error("开处方获取医保卡异常", e);
        }
        //创建请求体
        RecipeExtend recipeExtend = recipeExtendDAO.getByRecipeId(recipe.getRecipeId());
        EmrRecipeManager.getMedicalInfo(recipe, recipeExtend);
        RecipeSendRequestTO request = HisRequestInit.initRecipeSendRequestTO(recipe, details, patientBean, cardBean);
        //是否是武昌机构，替换请求体
        Set<String> organIdList = redisClient.sMembers(CacheConstant.KEY_WUCHANG_ORGAN_LIST);
        if (CollectionUtils.isNotEmpty(organIdList) && organIdList.contains(sendOrganId.toString())) {
            request = HisRequestInit.initRecipeSendRequestTOForWuChang(recipe, details, patientBean, cardBean);
            //发送电子病历
            DocIndexToHisReqTO docIndexToHisReqTO = HisRequestInit.initDocIndexToHisReqTO(recipe);
            HisResponseTO<DocIndexToHisResTO> hisResponseTO = service.docIndexToHis(docIndexToHisReqTO);
            if (hisResponseTO != null) {
                if ("200".equals(hisResponseTO.getMsgCode())) {
                    //电子病历接口返回挂号序号
                    if (hisResponseTO.getData() != null) {
                        request.setRegisteredId(hisResponseTO.getData().getRegisterId());
                        request.setRegisterNo(hisResponseTO.getData().getRegisterNo());
                        request.setPatientId(hisResponseTO.getData().getPatientId());
                    }
                    RecipeLogService.saveRecipeLog(recipe.getRecipeId(), recipe.getStatus(), recipe.getStatus(), "推送电子病历成功");
                } else {
                    RecipeLogService.saveRecipeLog(recipe.getRecipeId(), recipe.getStatus(), recipe.getStatus(), "推送电子病历失败。原因：" + hisResponseTO.getMsg());
                }
            }
        }
        request.setOrganID(sendOrganId.toString());
        LOGGER.info("recipeHisService recipeId:{} request:{}", recipeId, JSONUtils.toString(request));
        // 处方独立出来后,his根据域名来判断回调模块
        service.recipeSend(request);
    }

    private void doHisReturnSuccess(Recipe recipe) {
        PatientService patientService = BasicAPI.getService(PatientService.class);
        PatientDTO patientDTO = patientService.getPatientByMpiId(recipe.getMpiid());
        Date now = DateTime.now().toDate();
        String str = "";
        if (patientDTO != null && StringUtils.isNotEmpty(patientDTO.getCertificate())) {
            str = patientDTO.getCertificate().substring(patientDTO.getCertificate().length() - 5);
        }

        RecipeToHisCallbackService service = ApplicationUtils.getRecipeService(RecipeToHisCallbackService.class);
        HisSendResTO response = new HisSendResTO();
        response.setRecipeId(String.valueOf(recipe.getRecipeId()));
        List<OrderRepTO> repList = Lists.newArrayList();
        OrderRepTO orderRepTO = new OrderRepTO();
        //门诊号处理 年月日+患者身份证后5位 例：2019060407915
        orderRepTO.setPatientID(DateConversion.getDateFormatter(now, "yyMMdd") + str);
        orderRepTO.setRegisterID(orderRepTO.getPatientID());
        //生成处方编号，不需要通过HIS去产生
        String recipeCodeStr = DigestUtil.md5For16(recipe.getClinicOrgan() + recipe.getMpiid() + Calendar.getInstance().getTimeInMillis());
        orderRepTO.setRecipeNo(recipeCodeStr);
        repList.add(orderRepTO);
        response.setData(repList);
        service.sendSuccess(response);
        LOGGER.info("skip his success!!! recipeId={}", recipe.getRecipeId());
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
        LOGGER.info("recipeStatusUpdateWithOrganId  recipeId = {},otherOrganId={},hisRecipeStatus:{}", recipeId, otherOrganId, hisRecipeStatus);
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
            LOGGER.info("recipeStatusUpdateWithOrganId  sendOrganId:{}", sendOrganId);
            RecipeDetailDAO recipeDetailDAO = DAOFactory.getDAO(RecipeDetailDAO.class);
            RecipeToHisService service = AppContextHolder.getBean("recipeToHisService", RecipeToHisService.class);

            List<Recipedetail> details = recipeDetailDAO.findByRecipeId(recipeId);

            try {
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
                EmploymentService iEmploymentService = ApplicationUtils.getBasicService(EmploymentService.class);
                String jobNumber = iEmploymentService.getJobNumberByDoctorIdAndOrganIdAndDepartment(recipe.getDoctor(), recipe.getClinicOrgan(), recipe.getDepart());
                request.setDoctorNumber(jobNumber);
                LOGGER.info("recipeStatusUpdateWithOrganId  request:{}", request);
                flag = service.recipeUpdate(request);
            } catch (Exception e) {
                LOGGER.error("recipeStatusUpdateWithOrganId error ", e);
            }
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
//        if (skipHis(recipe)) {
//            return result;
//        }

        Integer status = recipe.getStatus();
        if (isHisEnable(recipe.getClinicOrgan())) {
            RecipeDetailDAO recipeDetailDAO = DAOFactory.getDAO(RecipeDetailDAO.class);
            RecipeToHisService service = AppContextHolder.getBean("recipeToHisService", RecipeToHisService.class);

            List<Recipedetail> details = recipeDetailDAO.findByRecipeId(recipeId);
            PatientBean patientBean = iPatientService.get(recipe.getMpiid());
            HealthCardBean cardBean = null;
            try {
                cardBean = iPatientService.getHealthCard(recipe.getMpiid(), recipe.getClinicOrgan(), "2");
            } catch (Exception e) {
                //打印日志，程序继续执行，不影响支付回调
                LOGGER.error("recipeDrugTake 获取健康卡失败:{},recipeId:{}.", e.getCause().getMessage(), recipe.getRecipeId(), e);
            }
            DrugTakeChangeReqTO request = HisRequestInit.initDrugTakeChangeReqTO(recipe, details, patientBean, cardBean);
            LOGGER.info("payNotify 请求参数:{}.", JSONUtils.toString(request));
            //线上支付完成需要发送消息（结算）（省医保则是医保结算）
            if (RecipeResultBean.SUCCESS.equals(result.getCode()) && 1 == payFlag) {
                //调用前置机结算支持两种方式---配送到家和药店取药
                if (RecipeBussConstant.PAYMODE_ONLINE.equals(recipe.getPayMode()) || RecipeBussConstant.PAYMODE_TFDS.equals(recipe.getPayMode())) {
                    //对卫宁收银台的订单不用再变更配送信息,走卫宁收银台已发送配送信息
                    if (StringUtils.isNotEmpty(recipe.getOrderCode())) {
                        RecipeOrderDAO recipeOrderDAO = DAOFactory.getDAO(RecipeOrderDAO.class);
                        RecipeOrder recipeOrder = recipeOrderDAO.getByOrderCode(recipe.getOrderCode());
                        // 111 为卫宁支付---卫宁付不走前置机的his结算
                        if (recipeOrder != null && !"111".equals(recipeOrder.getWxPayWay())) {
                            List<String> recipeIdList = (List<String>) JSONUtils.parse(recipeOrder.getRecipeIdList(), List.class);
                            PayNotifyReqTO payNotifyReq = HisRequestInit.initPayNotifyReqTO(recipeIdList, recipe, patientBean, cardBean);
                            PayNotifyResTO response = service.payNotify(payNotifyReq);
                            if (null != response && response.getMsgCode() == 0) {
                                //结算成功
                                if (response.getData() != null) {
                                    Recipedetail detail = new Recipedetail();
                                    detail.setPatientInvoiceNo(response.getData().getInvoiceNo());
                                    detail.setPharmNo(response.getData().getWindows());
                                    HisCallBackService.havePaySuccess(recipe.getRecipeId(), detail);
                                } else {
                                    HisCallBackService.havePaySuccess(recipe.getRecipeId(), null);
                                }

                            } else if ((null != response && (response.getMsgCode() != 0 || response.getMsg() != null)) || (response == null && "1".equals(payNotifyReq.getIsMedicalSettle()))) {
                                //前置机返回结算失败，或者医保结算前置机返回null
                                result.setCode(RecipeResultBean.FAIL);
                                if (response != null && response.getMsg() != null) {
                                    result.setError(response.getMsg());
                                } else {
                                    result.setError("由于医院接口异常，支付失败，建议您稍后重新支付。");
                                }
                                HisCallBackService.havePayFail(recipe.getRecipeId());
                                RecipeLogService.saveRecipeLog(recipe.getRecipeId(), status, status, "支付完成结算失败，his返回原因：" + response.getMsg());
                            } else {
                                //非医保结算前置机返回null可能未对接接口
                                LOGGER.error("payNotify 前置机未返回null，可能未对接结算接口");
                            }
                        }
                    }
                }
            }

            if (RecipeResultBean.SUCCESS.equals(result.getCode())) {
                Boolean success = service.drugTakeChange(request);
                //date 20200410
                //前置机为实现判断
                if (null == success) {
                    RecipeLogService.saveRecipeLog(recipe.getRecipeId(), status, status, "HIS更新购药方式返回：前置机未实现");
                } else if (success) {
                    RecipeLogService.saveRecipeLog(recipe.getRecipeId(), status, status, "HIS更新购药方式返回：写入his成功");
                } else {
                    RecipeLogService.saveRecipeLog(recipe.getRecipeId(), status, status, "HIS更新购药方式返回：写入his失败");
                    if (!RecipeBussConstant.GIVEMODE_TO_HOS.equals(recipe.getGiveMode())) {
                        LOGGER.error("HIS drugTake synchronize error. recipeId=" + recipeId);
                        //配送到家同步失败则返回异常,医院取药不需要管，医院处方默认是医院取药
                    }
                }
            }

        } else {
            RecipeLogService.saveRecipeLog(recipe.getRecipeId(), status, status, "recipeDrugTake[DrugTakeUpdateService] HIS未启用");
            LOGGER.error("recipeDrugTake 医院HIS未启用[organId:" + recipe.getClinicOrgan() + ",recipeId:" + recipe.getRecipeId() + "]");
        }

        return result;
    }

    @Autowired
    private RecipeDAO recipeDAO;

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
            //RecipeListQueryReqTO request = new RecipeListQueryReqTO(recipeCodes, organId);
            List<RecipeListQueryReqTO> requestList = new ArrayList<>();
            for (String recipeCode : recipeCodes) {
                Recipe recipe = recipeDAO.getByRecipeCodeAndClinicOrgan(recipeCode, organId);
                RecipeExtend recipeExtend = recipeExtendDAO.getByRecipeId(recipe.getRecipeId());
                RecipeListQueryReqTO recipeListQueryReqTO = new RecipeListQueryReqTO();
                recipeListQueryReqTO.setCertID(patientService.getPatientBeanByMpiId(recipe.getMpiid()) == null ? null : patientService.getPatientBeanByMpiId(recipe.getMpiid()).getCardId());
                recipeListQueryReqTO.setOrganID((null != organId) ? Integer.toString(organId) : null);
                recipeListQueryReqTO.setCardNo(recipeExtend == null ? null : recipeExtend.getCardNo());
                recipeListQueryReqTO.setCardType(recipeExtend == null ? null : recipeExtend.getCardType());
                recipeListQueryReqTO.setPatientName(recipe.getPatientName());
                recipeListQueryReqTO.setPatientId(recipe.getPatientID());
                recipeListQueryReqTO.setRegisterId(recipeExtend == null ? null : recipeExtend.getRegisterID());
                recipeListQueryReqTO.setRecipeNo(recipe.getRecipeCode());
                requestList.add(recipeListQueryReqTO);
            }
            service.listQuery(requestList);
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
            } else {
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
            //健康卡数据上传
            RecipeBusiThreadPool.execute(new CardDataUploadRunable(recipe.getClinicOrgan(), recipe.getMpiid(), "010103"));
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
            //RecipeListQueryReqTO request = new RecipeListQueryReqTO(recipe.getRecipeCode(), recipe.getClinicOrgan());
            //TODO DINGXX  设置患者姓名
            List<RecipeListQueryReqTO> requestList = new ArrayList<>();
            RecipeExtend recipeExtend = recipeExtendDAO.getByRecipeId(recipeId);
            RecipeListQueryReqTO recipeListQueryReqTO = new RecipeListQueryReqTO();
            recipeListQueryReqTO.setCertID(patientService.getPatientBeanByMpiId(recipe.getMpiid()).getCardId());
            recipeListQueryReqTO.setOrganID((null != recipe.getClinicOrgan()) ? Integer.toString(recipe.getClinicOrgan()) : null);
            recipeListQueryReqTO.setCardNo(recipeExtend.getCardNo());
            recipeListQueryReqTO.setCardType(recipeExtend.getCardType());
            recipeListQueryReqTO.setPatientName(recipe.getPatientName());
            recipeListQueryReqTO.setPatientId(recipe.getPatientID());
            recipeListQueryReqTO.setRegisterId(recipeExtend.getRegisterID());
            recipeListQueryReqTO.setRecipeNo(recipe.getRecipeCode());
            requestList.add(recipeListQueryReqTO);
            Integer status = service.listSingleQuery(requestList);
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
                List<DrugInfoHisBean> drugInfoList = organDrugListDAO.findDrugInfoByOrganId(organId, start, 1);
                if (CollectionUtils.isNotEmpty(drugInfoList)) {
                    requestList = Lists.newArrayList();
                    DrugInfoTO drugInfoTO;
                    for (DrugInfoHisBean drugInfoHisBean : drugInfoList) {
                        drugInfoTO = new DrugInfoTO();
                        BeanUtils.copyProperties(drugInfoHisBean, drugInfoTO);
                        requestList.add(drugInfoTO);
                    }
                    List<DrugInfoTO> drugInfoTOs = service.queryDrugInfo(requestList, organId);
                    List<String> drugCodes = requestList.stream().map(DrugInfoTO::getDrcode).collect(Collectors.toList());
                    if (CollectionUtils.isEmpty(drugInfoTOs)) {
                        LOGGER.warn("queryDrugInfo 药品code集合{}未查询到医院药品数据", drugCodes);
                        backList = new ArrayList<>();
                    } else {
                        backList = drugInfoTOs;
                    }
                }
            }

            return backList;
        } else {
            LOGGER.error("getDrugInfoFromHis 医院HIS未启用[organId:" + organId + "]");
        }

        return null;
    }

    /**
     * 处方省医保预结算接口+杭州市互联网预结算接口
     *
     * @param recipeId
     * @param extInfo  扩展信息
     * @return
     */
    @RpcService
    public Map<String, Object> provincialMedicalPreSettle(Integer recipeId, Map<String, String> extInfo) {
        Map<String, Object> result = Maps.newHashMap();
        result.put("code", "-1");
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        RecipeExtendDAO recipeExtendDAO = DAOFactory.getDAO(RecipeExtendDAO.class);
        RecipeOrderDAO recipeOrderDAO = DAOFactory.getDAO(RecipeOrderDAO.class);
        Recipe recipe = recipeDAO.getByRecipeId(recipeId);
        if (recipe == null) {
            result.put("msg", "查不到该处方");
            return result;
        }
        try {
            MedicalPreSettleReqNTO request = new MedicalPreSettleReqNTO();
            request.setClinicId(String.valueOf(recipe.getClinicId()));
            request.setClinicOrgan(recipe.getClinicOrgan());
            request.setRecipeId(String.valueOf(recipeId));
            request.setHisRecipeNo(recipe.getRecipeCode());
            String recipeCodeS = MapValueUtil.getString(extInfo, "recipeNoS");
            if (recipeCodeS != null) {
                request.setHisRecipeNoS(JSONUtils.parse(recipeCodeS, ArrayList.class));
            }
            request.setDoctorId(recipe.getDoctor() + "");
            request.setDoctorName(recipe.getDoctorName());
            request.setDepartId(recipe.getDepart() + "");
            //参保地区行政区划代码
            request.setInsuredArea(MapValueUtil.getString(extInfo, "insuredArea"));
            IConfigurationCenterUtilsService configService = BaseAPI.getService(IConfigurationCenterUtilsService.class);
            //获取医保支付流程配置（2-原省医保 3-长三角）
            Integer insuredAreaType = (Integer) configService.getConfiguration(recipe.getClinicOrgan(), "provincialMedicalPayFlag");
            if (new Integer(3).equals(insuredAreaType)) {
                if (StringUtils.isEmpty(request.getInsuredArea())) {
                    result.put("msg", "参保地区行政区划代码为空,无法进行预结算");
                    return result;
                }
                //省医保参保类型 1 长三角 没有赋值就是原来的省直医保
                request.setInsuredAreaType("1");
            }
            RecipeExtend ext = recipeExtendDAO.getByRecipeId(recipe.getRecipeId());
            if (ext != null) {
                /*//查询已经预结算过的挂号序号
                List<RecipeExtend> recipeExtends = recipeExtendDAO.querySettleRecipeExtendByRegisterID(ext.getRegisterID());
                if (CollectionUtils.isEmpty(recipeExtends)) {
                    //his作为是否返回诊察费的判断  诊察费再总金额里返回*/
                if (StringUtils.isNotEmpty(ext.getRegisterID())) {
                    request.setRegisterID(ext.getRegisterID());
                }
                //默认是医保，医生选择了自费时，强制设置为自费
                if (ext.getMedicalType() != null && "0".equals(ext.getMedicalType())) {
                    request.setIszfjs("1");
                } else {
                    request.setIszfjs("0");
                }
            }
            try {
                request.setDepartName(DictionaryController.instance().get("eh.base.dictionary.Depart").getText(recipe.getDepart()));
            } catch (ControllerException e) {
                LOGGER.warn("provincialMedicalPreSettle 字典转化异常");
            }
            //患者信息
            PatientService patientService = BasicAPI.getService(PatientService.class);
            PatientDTO patientBean = patientService.get(recipe.getMpiid());
            request.setPatientName(patientBean.getPatientName());
            request.setIdcard(patientBean.getIdcard());
            request.setBirthday(patientBean.getBirthday());
            request.setAddress(patientBean.getAddress());
            request.setMobile(patientBean.getMobile());
            request.setGuardianName(patientBean.getGuardianName());
            request.setGuardianTel(patientBean.getLinkTel());
            request.setGuardianCertificate(patientBean.getGuardianCertificate());

            DrugsEnterpriseDAO drugEnterpriseDao = DAOFactory.getDAO(DrugsEnterpriseDAO.class);
            Integer depId = MapValueUtil.getInteger(extInfo, "depId");
            //获取杭州市市民卡
            if (depId != null) {
                DrugsEnterprise drugEnterprise = drugEnterpriseDao.get(depId);
                if (drugEnterprise != null) {
                    HealthCardService healthCardService = ApplicationUtils.getBasicService(HealthCardService.class);
                    //杭州市互联网医院监管中心 管理单元eh3301
                    OrganService organService = ApplicationUtils.getBasicService(OrganService.class);
                    OrganDTO organDTO = organService.getByManageUnit("eh3301");
                    String bxh = null;
                    if (organDTO != null) {
                        bxh = healthCardService.getMedicareCardId(recipe.getMpiid(), organDTO.getOrganId());
                    }
                    request.setBxh(bxh);
                }
            }

            RecipeToHisService service = AppContextHolder.getBean("recipeToHisService", RecipeToHisService.class);
            LOGGER.info("provincialMedicalPreSettle recipeId={} req={}", recipeId, JSONUtils.toString(request));
            HisResponseTO<RecipeMedicalPreSettleInfo> hisResult = service.recipeMedicalPreSettleN(request);
            LOGGER.info("provincialMedicalPreSettle recipeId={} res={}", recipeId, JSONUtils.toString(hisResult));
            if (hisResult != null && "200".equals(hisResult.getMsgCode())) {
                if (hisResult.getData() != null) {
                    //自费金额
                    String cashAmount = hisResult.getData().getZfje();
                    //医保支付金额
                    String fundAmount = hisResult.getData().getYbzf();
                    //总金额
                    String totalAmount = hisResult.getData().getZje();
                    if (ext != null) {
                        Map<String, String> map = Maps.newHashMap();
                        //杭州互联网用到registerNo、hisSettlementNo
                        map.put("registerNo", hisResult.getData().getGhxh());
                        map.put("hisSettlementNo", hisResult.getData().getSjh());
                        //平台和杭州互联网都用到
                        map.put("preSettleTotalAmount", totalAmount);
                        map.put("fundAmount", fundAmount);
                        map.put("cashAmount", cashAmount);
                        //仅省医保用到insuredArea
                        if (StringUtils.isNotEmpty(request.getInsuredArea())) {
                            map.put("insuredArea", request.getInsuredArea());
                        }
                        recipeExtendDAO.updateRecipeExInfoByRecipeId(recipe.getRecipeId(), map);
                        //此时订单已经生成还需要更新订单信息
                        RecipeOrder recipeOrder = recipeOrderDAO.getByOrderCode(recipe.getOrderCode());
                        if (recipeOrder != null) {
                            RecipeOrderService recipeOrderService = ApplicationUtils.getRecipeService(RecipeOrderService.class);
                            if (!recipeOrderService.dealWithOrderInfo(map, recipeOrder, recipe)) {
                                result.put("msg", "预结算更新订单信息失败");
                                return result;
                            }
                        }
                    } else {
                        //此时ext一般已经存在，若不存在有问题
                        LOGGER.error("provincialMedicalPreSettle-fail. recipeId={} recipeExtend is null", recipeId);
                        ext = new RecipeExtend();
                        ext.setRecipeId(recipe.getRecipeId());
                        ext.setRegisterNo(hisResult.getData().getGhxh());
                        ext.setHisSettlementNo(hisResult.getData().getSjh());
                        ext.setPreSettletotalAmount(hisResult.getData().getZje());
                        ext.setFundAmount(hisResult.getData().getYbzf());
                        ext.setCashAmount(hisResult.getData().getYfje());
                        recipeExtendDAO.save(ext);
                    }
                    result.put("totalAmount", totalAmount);
                    result.put("fundAmount", fundAmount);
                    result.put("cashAmount", cashAmount);
                }
                result.put("code", "200");
                //日志记录
                RecipeLogService.saveRecipeLog(recipeId, recipe.getStatus(), recipe.getStatus(), "处方预结算成功");
            } else {
                String msg;
                if (hisResult != null) {
                    msg = "his返回:" + hisResult.getMsg();
                } else {
                    msg = "前置机未实现预结算接口";
                }
                result.put("msg", msg);
                //日志记录
                RecipeLogService.saveRecipeLog(recipeId, recipe.getStatus(), recipe.getStatus(), "处方预结算失败-原因:" + msg);
            }
        } catch (Exception e) {
            LOGGER.error("provincialMedicalPreSettle recipeId={} error", recipeId, e);
            throw new DAOException(609, "处方预结算异常");
        }
        return result;
    }


    private Map<String, Object> sendMsgResultMap(Recipe dbRecipe, Map<String, String> extInfo, Map<String, Object> payResult) {
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        PurchaseService purchaseService = ApplicationUtils.getRecipeService(PurchaseService.class);
        PayModeOnline service = (PayModeOnline) purchaseService.getService(1);
        HisResponseTO resultSave = service.updateGoodsReceivingInfoToCreateOrder(dbRecipe.getRecipeId(), extInfo);

        if (null != resultSave) {
            if (resultSave.isSuccess() && null != resultSave.getData()) {

                Map<String, Object> data = (Map<String, Object>) resultSave.getData();

                if (null != data.get("recipeCode")) {
                    //新增成功更新his处方code
                    recipeDAO.updateRecipeInfoByRecipeId(dbRecipe.getRecipeId(), ImmutableMap.of("recipeCode", data.get("recipeCode").toString()));
                    LOGGER.info("order 当前处方{}确认订单流程：his新增成功", dbRecipe.getRecipeId());
                    return payResult;
                } else {
                    payResult.put("code", "-1");
                    payResult.put("msg", "订单信息校验失败");
                    LOGGER.info("order 当前处方确认订单的his同步配送信息，没有返回his处方code：{}", JSONUtils.toString(resultSave));
                    return payResult;
                }
            } else {
                payResult.put("code", "-1");
                payResult.put("msg", "订单信息校验失败");
                LOGGER.info("order 当前处方确认订单的his同步配送信息失败，返回：{}", JSONUtils.toString(resultSave));
                return payResult;
            }
        } else {
            LOGGER.info("order 当前处方{}没有对接同步配送信息，默认成功！", dbRecipe.getRecipeId());
            return payResult;
        }
    }

    /**
     * 处方自费预结算接口
     *
     * @param recipeId
     * @param payMode
     * @return
     */
    @RpcService
    public Map<String, Object> provincialCashPreSettle(Integer recipeId, Integer payMode) {
        Map<String, Object> result = Maps.newHashMap();
        result.put("code", "-1");
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        RecipeExtendDAO recipeExtendDAO = DAOFactory.getDAO(RecipeExtendDAO.class);
        RecipeOrderDAO recipeOrderDAO = DAOFactory.getDAO(RecipeOrderDAO.class);
        Recipe recipe = recipeDAO.getByRecipeId(recipeId);
        if (recipe == null) {
            result.put("msg", "查不到该处方");
            return result;
        }
        try {
            RecipeCashPreSettleReqTO request = new RecipeCashPreSettleReqTO();
            //购药方式
            if (PurchaseEnum.PAYMODE_ONLINE.getPayMode().equals(payMode)) {
                //配送到家
                request.setDeliveryType("1");
            } else if (PurchaseEnum.PAYMODE_TO_HOS.getPayMode().equals(payMode)) {
                //到院取药
                request.setDeliveryType("0");
            }
            request.setClinicOrgan(recipe.getClinicOrgan());
            request.setRecipeId(String.valueOf(recipeId));
            request.setHisRecipeNo(recipe.getRecipeCode());
            //患者信息
            PatientService patientService = BasicAPI.getService(PatientService.class);
            PatientDTO patientBean = patientService.get(recipe.getMpiid());
            request.setPatientName(patientBean.getPatientName());
            request.setIdcard(patientBean.getIdcard());
            RecipeToHisService service = AppContextHolder.getBean("recipeToHisService", RecipeToHisService.class);
            LOGGER.info("provincialCashPreSettle recipeId={} req={}", recipeId, JSONUtils.toString(request));
            HisResponseTO<RecipeCashPreSettleInfo> hisResult = service.recipeCashPreSettleHis(request);
            if (hisResult != null && "200".equals(hisResult.getMsgCode())) {
                LOGGER.info("provincialCashPreSettle-true.recipeId={} result={}", recipeId, JSONUtils.toString(hisResult));
                if (hisResult.getData() != null) {
                    //自费金额
                    String cashAmount = hisResult.getData().getZfje();
                    //应付金额----上海六院新增
                    String payAmount = hisResult.getData().getYfje();
                    //总金额
                    String totalAmount = hisResult.getData().getZje();
                    //his收据号
                    String hisSettlementNo = hisResult.getData().getSjh();
                    if (StringUtils.isNotEmpty(cashAmount) && StringUtils.isNotEmpty(totalAmount)) {
                        RecipeExtend ext = recipeExtendDAO.getByRecipeId(recipe.getRecipeId());
                        if (ext != null) {
                            Map<String, String> map = Maps.newHashMap();
                            map.put("preSettleTotalAmount", totalAmount);
                            map.put("cashAmount", cashAmount);
                            map.put("hisSettlementNo", hisSettlementNo);
                            map.put("payAmount", payAmount);
                            recipeExtendDAO.updateRecipeExInfoByRecipeId(recipe.getRecipeId(), map);
                            //订单信息更新
                            RecipeOrder recipeOrder = recipeOrderDAO.getByOrderCode(recipe.getOrderCode());
                            if (recipeOrder != null) {
                                RecipeOrderService recipeOrderService = ApplicationUtils.getRecipeService(RecipeOrderService.class);
                                if (!recipeOrderService.dealWithOrderInfo(map, recipeOrder, recipe)) {
                                    result.put("msg", "预结算更新订单信息失败");
                                    return result;
                                }
                            }
                        } else {
                            ext = new RecipeExtend();
                            ext.setRecipeId(recipe.getRecipeId());
                            ext.setPreSettletotalAmount(totalAmount);
                            ext.setCashAmount(cashAmount);
                            ext.setHisSettlementNo(hisSettlementNo);
                            ext.setPayAmount(payAmount);
                            recipeExtendDAO.save(ext);
                        }
                    }
                    result.put("totalAmount", totalAmount);
                    result.put("cashAmount", cashAmount);
                }
                result.put("code", "200");
                //日志记录
                RecipeLogService.saveRecipeLog(recipeId, recipe.getStatus(), recipe.getStatus(), "处方自费预结算成功");
            } else if (hisResult != null && "0".equals(hisResult.getMsgCode())) {
                result.put("code", "200");
                //日志记录
                RecipeLogService.saveRecipeLog(recipeId, recipe.getStatus(), recipe.getStatus(), "处方自费预结算成功，无返回值");
            } else {
                LOGGER.error("provincialCashPreSettle-fail.recipeId={} result={}", recipeId, JSONUtils.toString(hisResult));
                String msg;
                if (hisResult != null) {
                    msg = "his返回:" + hisResult.getMsg();
                } else {
                    msg = "平台前置机未实现自费预结算接口";
                }
                result.put("msg", msg);
                //日志记录
                RecipeLogService.saveRecipeLog(recipeId, recipe.getStatus(), recipe.getStatus(), "处方自费预结算失败,原因:" + msg);
            }
        } catch (Exception e) {
            LOGGER.error("provincialCashPreSettle recipeId={} error", recipeId, e);
        }
        return result;
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
        return scanDrugStock(recipe, detailList);
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
                    result.setObject(nameList);
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
     *
     * @param recipe
     * @return
     */
    public RecipeResultBean recipeAudit(Recipe recipe, CheckYsInfoBean resutlBean) {
        RecipeResultBean result = RecipeResultBean.getSuccess();
        if (isHisEnable(recipe.getClinicOrgan())) {
            RecipeToHisService service = AppContextHolder.getBean("recipeToHisService", RecipeToHisService.class);
            PatientBean patientBean = iPatientService.get(recipe.getMpiid());
            RecipeAuditReqTO request = HisRequestInit.recipeAudit(recipe, patientBean, resutlBean);
            LOGGER.info("recipeAudit req={}", JSONUtils.toString(request));
            HisResponseTO response = service.recipeAudit(request);
            LOGGER.info("recipeAudit res={}", JSONUtils.toString(response));
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
     *
     * @param recipeId
     * @return
     */
    public RecipeResultBean docIndexToHis(Integer recipeId) {
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
            String[] recipeTypes = (String[]) configurationCenterUtilsService.getConfiguration(recipe.getClinicOrgan(), "getRecipeTypeToHis");
            List<String> recipeTypelist = Arrays.asList(recipeTypes);
            if (recipeTypelist.contains(Integer.toString(recipe.getRecipeType()))) {
                return false;
            }
        } catch (Exception e) {
            LOGGER.error("skipHis error " + e.getMessage(), e);
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

    @RpcService
    public boolean hisRecipeCheck(Map<String, Object> rMap, RecipeBean recipeBean) {
        RecipeDetailDAO detailDAO = DAOFactory.getDAO(RecipeDetailDAO.class);
        List<Recipedetail> details = detailDAO.findByRecipeId(recipeBean.getRecipeId());

        HisCheckRecipeReqTO hisCheckRecipeReqTO = new HisCheckRecipeReqTO();
        OrganService organService = BasicAPI.getService(OrganService.class);
        DepartmentService departmentService = BasicAPI.getService(DepartmentService.class);
        hisCheckRecipeReqTO.setClinicOrgan(recipeBean.getClinicOrgan());
        hisCheckRecipeReqTO.setOrganID(organService.getOrganizeCodeByOrganId(recipeBean.getClinicOrgan()));
        if (recipeBean.getClinicId() != null) {
            IRevisitExService exService = RevisitAPI.getService(IRevisitExService.class);
            RevisitExDTO exDTO = exService.getByConsultId(recipeBean.getClinicId());
            if (null != exDTO) {
                hisCheckRecipeReqTO.setClinicID(exDTO.getRegisterNo());
            } else {
                LOGGER.info("当前处方{}的复诊{}信息为空", recipeBean.getRecipeId(), recipeBean.getClinicId());
            }
        }
        hisCheckRecipeReqTO.setRecipeID(null != recipeBean.getRecipeCode() ? recipeBean.getRecipeCode() : recipeBean.getRecipeId().toString());
        hisCheckRecipeReqTO.setPlatRecipeID(recipeBean.getRecipeId());
        hisCheckRecipeReqTO.setIsLongRecipe(recipeBean.getRecipeExtend().getIsLongRecipe());
        IPatientService iPatientService = ApplicationUtils.getBaseService(IPatientService.class);
        //患者信息
        PatientBean patientBean = iPatientService.get(recipeBean.getMpiid());
        if (null != patientBean) {
            //身份证
            hisCheckRecipeReqTO.setCertID(patientBean.getIdcard());
            //患者名
            hisCheckRecipeReqTO.setPatientName(patientBean.getPatientName());
            //患者性别
            hisCheckRecipeReqTO.setPatientSex(patientBean.getPatientSex());
            //患者电话
            hisCheckRecipeReqTO.setPatientTel(patientBean.getMobile());
            //病人类型
        }
        //医生工号
        EmploymentService iEmploymentService = ApplicationUtils.getBasicService(EmploymentService.class);
        if (recipeBean.getDoctor() != null) {
            String jobNumber = iEmploymentService.getJobNumberByDoctorIdAndOrganIdAndDepartment(recipeBean.getDoctor(), recipeBean.getClinicOrgan(), recipeBean.getDepart());
            hisCheckRecipeReqTO.setDoctorID(jobNumber);
            DoctorService doctorService = ApplicationUtils.getBasicService(DoctorService.class);
            hisCheckRecipeReqTO.setDoctorName(recipeBean.getDoctorName());
        }
        //处方数量
        hisCheckRecipeReqTO.setRecipeNum("1");
        //诊断代码
        if (StringUtils.isNotBlank(recipeBean.getOrganDiseaseId())) {
            hisCheckRecipeReqTO.setIcdCode(RecipeUtil.getCode(recipeBean.getOrganDiseaseId()));
        }
        //诊断名称
        if (StringUtils.isNotBlank(recipeBean.getOrganDiseaseName())) {
            hisCheckRecipeReqTO.setIcdName(RecipeUtil.getCode(recipeBean.getOrganDiseaseName()));
        }
        //科室代码---行政科室代码
        DepartmentDTO departmentDTO = departmentService.getById(recipeBean.getDepart());
        if (departmentDTO != null) {
            hisCheckRecipeReqTO.setDeptCode(departmentDTO.getCode());
            hisCheckRecipeReqTO.setDeptName(departmentDTO.getName());
        }
        //开单时间
        hisCheckRecipeReqTO.setRecipeDate(DateConversion.formatDateTimeWithSec(recipeBean.getSignDate()));
        //处方类别
        hisCheckRecipeReqTO.setRecipeType(String.valueOf(recipeBean.getRecipeType()));
        //处方金额
        hisCheckRecipeReqTO.setRecipePrice(recipeBean.getTotalMoney());
        //orderList
        List<RecipeOrderItemTO> list = Lists.newArrayList();
        OrganDrugListDAO organDrugListDAO = DAOFactory.getDAO(OrganDrugListDAO.class);
        if (null != details && !details.isEmpty()) {
            for (Recipedetail detail : details) {
                RecipeOrderItemTO item = new RecipeOrderItemTO();
                OrganDrugList organDrug = organDrugListDAO.getByOrganIdAndOrganDrugCodeAndDrugId(recipeBean.getClinicOrgan(), detail.getOrganDrugCode(), detail.getDrugId());
                if (StringUtils.isNotEmpty(detail.getUseDoseStr())) {
                    item.setDosage(detail.getUseDoseStr());
                } else {
                    item.setDosage((null != detail.getUseDose()) ? Double.toString(detail.getUseDose()) : null);
                }
                item.setDrcode(detail.getOrganDrugCode());
                item.setDrname(detail.getDrugName());
                if (organDrug != null) {
                    item.setDrugManf(organDrug.getProducer());
                    //药品产地编码
                    item.setManfCode(organDrug.getProducerCode());
                    //药品单价
                    item.setPrice(organDrug.getSalePrice());
                }
                //频次
                item.setFrequency(UsingRateFilter.filterNgari(recipeBean.getClinicOrgan(), detail.getUsingRate()));
                //用法
                item.setAdmission(UsePathwaysFilter.filterNgari(recipeBean.getClinicOrgan(), detail.getUsePathways()));
                //机构用法代码
                item.setOrganUsePathways(detail.getOrganUsePathways());
                //机构频次代码
                item.setOrganUsingRate(detail.getOrganUsingRate());
                //用药天数
                item.setUseDays(Integer.toString(detail.getUseDays()));
                //剂量单位
                item.setDrunit(detail.getUseDoseUnit());
                // 开药数量
                item.setTotalDose((null != detail.getUseTotalDose()) ? Double.toString(detail.getUseTotalDose()) : null);
                //药品单位
                item.setUnit(detail.getDrugUnit());
                //药品规格
                item.setDrModel(detail.getDrugSpec());
                //药品包装
                item.setPack(String.valueOf(detail.getPack()));
                //药品包装单位
                item.setPackUnit(detail.getDrugUnit());
                //备注
                item.setRemark(detail.getMemo());
                //date 20200222 杭州市互联网添加字段
                item.setDrugID(detail.getDrugId());
                //date 20200701 预校验添加平台药品医嘱ID
                item.setOrderID(detail.getRecipeDetailId().toString());
                // 黄河医院 剂型名称
                item.setDrugForm(detail.getDrugForm());
                list.add(item);
            }
            hisCheckRecipeReqTO.setOrderList(list);
        }
        //date 20200222杭州市互联网(添加诊断)
        List<DiseaseInfo> diseaseInfos = new ArrayList<>();
        DiseaseInfo diseaseInfo;
        if (StringUtils.isNotEmpty(recipeBean.getOrganDiseaseId()) && StringUtils.isNotEmpty(recipeBean.getOrganDiseaseName())) {
            String[] diseaseIds = recipeBean.getOrganDiseaseId().split("；");
            String[] diseaseNames = recipeBean.getOrganDiseaseName().split("；");
            for (int i = 0; i < diseaseIds.length; i++) {
                diseaseInfo = new DiseaseInfo();
                diseaseInfo.setDiseaseCode(diseaseIds[i]);
                diseaseInfo.setDiseaseName(diseaseNames[i]);
                diseaseInfos.add(diseaseInfo);
            }
            hisCheckRecipeReqTO.setDiseaseInfo(diseaseInfos);

        }


        RecipeToHisService service = AppContextHolder.getBean("recipeToHisService", RecipeToHisService.class);
        LOGGER.info("hisRecipeCheck req={}", JSONUtils.toString(hisCheckRecipeReqTO));
        HisResponseTO hisResult = service.hisCheckRecipe(hisCheckRecipeReqTO);
        LOGGER.info("hisRecipeCheck res={}", JSONUtils.toString(hisResult));
        if (hisResult == null) {
            rMap.put("signResult", false);
            rMap.put("errorFlag", true);
            rMap.put("errorMsg", "his返回结果null");
            return false;
        }
        if ("200".equals(hisResult.getMsgCode())) {
            Map<String, Object> map = (Map<String, Object>) hisResult.getData();
            if ("0".equals(map.get("checkResult"))) {
                rMap.put("signResult", false);
                rMap.put("errorFlag", true);
                rMap.put("errorMsg", map.get("resultMark"));
            } else {
                RemoteDrugEnterpriseService remoteDrugEnterpriseService = ApplicationUtils.getRecipeService(RemoteDrugEnterpriseService.class);

                OrganAndDrugsepRelationDAO relationDAO = DAOFactory.getDAO(OrganAndDrugsepRelationDAO.class);
                List<DrugsEnterprise> enterprises = relationDAO.findDrugsEnterpriseByOrganIdAndStatus(recipeBean.getClinicOrgan(), 1);
                AccessDrugEnterpriseService remoteService = null;
                if (null != enterprises) {
                    remoteService = remoteDrugEnterpriseService.getServiceByDep(enterprises.get(0));
                }
                if (null == remoteService) {
                    remoteService = getBean("commonRemoteService", CommonRemoteService.class);
                }
                remoteService.checkRecipeGiveDeliveryMsg(recipeBean, map);

                return "1".equals(map.get("checkResult"));


            }
        } else {
            rMap.put("signResult", false);
            rMap.put("errorFlag", true);
            rMap.put("errorMsg", hisResult.getMsg());
        }
        return false;
    }

    /**
     * 武昌基础药品数据同步给his
     *
     * @param drugLists
     */
    @RpcService
    public void syncDrugListToHis(List<DrugList> drugLists) {
        RecipeToHisService service = AppContextHolder.getBean("recipeToHisService", RecipeToHisService.class);
        OrganService organService = ApplicationUtils.getBasicService(OrganService.class);

        List<DrugList> list = Lists.newArrayList();
        for (DrugList drugList : drugLists) {
            //武昌机构用的药品基础药品数据sourceorgan都为1001780
            if (drugList.getSourceOrgan() == 1001780) {
                //double失真处理
                if (drugList.getUseDose() != null) {
                    drugList.setUseDose(BigDecimal.valueOf(drugList.getUseDose()).doubleValue());
                }
                if (drugList.getPrice1() != null) {
                    drugList.setPrice1(BigDecimal.valueOf(drugList.getPrice1()).doubleValue());

                } else {
                    drugList.setPrice1(0.0);
                }
                drugList.setPrice2(drugList.getPrice1());
                list.add(drugList);
            }
        }
        if (CollectionUtils.isEmpty(list)) {
            return;
        }
        //武昌机构集合
        Set<String> organIdList = redisClient.sMembers(CacheConstant.KEY_WUCHANG_ORGAN_LIST);
        SyncDrugListToHisReqTO request;
        List<DrugListTO> drugListTO = ObjectCopyUtils.convert(list, DrugListTO.class);
        for (String organId : organIdList) {
            request = new SyncDrugListToHisReqTO();
            request.setClinicOrgan(Integer.valueOf(organId));
            //组织机构编码
            request.setOrganCode(organService.getOrganizeCodeByOrganId(Integer.valueOf(organId)));
            request.setDrugList(drugListTO);
            service.syncDrugListToHis(request);
        }
    }

    public MedicInsurSettleApplyResTO recipeMedicInsurPreSettle(MedicInsurSettleApplyReqTO reqTO) {
        RecipeToHisService service = AppContextHolder.getBean("recipeToHisService", RecipeToHisService.class);
        LOGGER.info("调用his接口recipeMedicInsurPreSettle，入参：{}", JSONUtils.toString(reqTO));
        HisResponseTO<MedicInsurSettleApplyResTO> hisResponseTO = service.recipeMedicInsurPreSettle(reqTO);
        LOGGER.info("调用his接口recipeMedicInsurPreSettle，出参：{},idCard = {}", JSONUtils.toString(hisResponseTO), reqTO.getCertId());
        if (null == hisResponseTO || !"200".equals(hisResponseTO.getMsgCode())) {
            throw new DAOException(hisResponseTO == null ? "医保结算申请失败" : hisResponseTO.getMsg());
        }
        return hisResponseTO.getData();
    }

    /**
     * 医院在复诊/处方结算完成的时候将电子票据号同步到结算上
     */
    @RpcService
    public HisResponseTO syncEinvoiceNumberToPay(SyncEinvoiceNumberDTO syncEinvoiceNumberDTO) {
        //判断当前传入的信息是否满足定位更新电子票据号
        //满足则更新支付的电子票据号
        HisResponseTO result = new HisResponseTO();
        result.setMsgCode("0");
        if (!valiSyncEinvoiceNumber(syncEinvoiceNumberDTO, result)) {
            return result;
        }
        IHosrelationService hosrelationService = BaseAPI.getService(IHosrelationService.class);
        //判断复诊的支付或者处方的支付能否定位到
        HosrelationBean hosrelation = hosrelationService.getByStatusAndInvoiceNoAndOrganId(1, syncEinvoiceNumberDTO.getInvoiceNo(), Integer.parseInt(syncEinvoiceNumberDTO.getOrganId()));

        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        RecipeExtendDAO recipeExtendDAO = DAOFactory.getDAO(RecipeExtendDAO.class);
        RecipeDetailDAO recipeDetailDAO = DAOFactory.getDAO(RecipeDetailDAO.class);
        Integer recipeId = recipeDetailDAO.getRecipeIdByOrganIdAndInvoiceNo(Integer.parseInt(syncEinvoiceNumberDTO.getOrganId()), syncEinvoiceNumberDTO.getInvoiceNo());

        if (null != hosrelation) {
            hosrelationService.updateEinvoiceNumberById(hosrelation.getId(), syncEinvoiceNumberDTO.getEinvoiceNumber());
            result.setSuccess();
            return result;

        }
        if (null != recipeId) {
            Boolean updateResult = recipeExtendDAO.updateRecipeExInfoByRecipeId(recipeId, ImmutableMap.of("einvoiceNumber", syncEinvoiceNumberDTO.getEinvoiceNumber()));
            if (updateResult) {
                result.setSuccess();
                return result;
            } else {
                result.setMsg("更新电子票据号失败！");
            }
        }
        result.setMsg("当前无支付订单与支付单号对应，更新电子票据号失败！");
        return result;
    }

    private boolean valiSyncEinvoiceNumber(SyncEinvoiceNumberDTO syncEinvoiceNumberDTO, HisResponseTO result) {
        boolean flag = true;
        if (null == syncEinvoiceNumberDTO) {
            result.setMsg("当前医院更新电子票据号，请求参数为空！");
            flag = false;
        }
        if (StringUtils.isEmpty(syncEinvoiceNumberDTO.getOrganId()) || StringUtils.isEmpty(syncEinvoiceNumberDTO.getInvoiceNo())) {
            result.setMsg("当前医院更新电子票据号，传入的机构id或者HIS结算单据号无法更新！");
            flag = false;
        }
        if (StringUtils.isEmpty(syncEinvoiceNumberDTO.getEinvoiceNumber())) {
            result.setMsg("当前医院更新电子票据号，传入更新的电子票据号为空无法更新！");
            flag = false;
        }
        return flag;
    }

    @RpcService
    public List<HzyyRationalUseDrugResTO> queryHzyyRationalUserDurg(HzyyRationalUseDrugReqTO reqTO) {
        LOGGER.info("调用杭州逸曜合理用药queryHzyyRationalUserDurg,入参 = {}，idNO = {}", JSONUtils.toString(reqTO), reqTO.getPatient().getIdNo());
        IRecipeHisService iRecipeHisService = AppContextHolder.getBean("his.iRecipeHisService", IRecipeHisService.class);
        HisResponseTO<List<HzyyRationalUseDrugResTO>> hisResponseTO = iRecipeHisService.queryHzyyRationalUserDurg(reqTO);
        LOGGER.info("调用杭州逸曜合理用药queryHzyyRationalUserDurg,出参 = {}, idNO = {}", JSONUtils.toString(hisResponseTO), reqTO.getPatient().getIdNo());
        if (hisResponseTO == null || !hisResponseTO.getMsgCode().equals("200")) {
            return Collections.EMPTY_LIST;
        }
        return hisResponseTO.getData();
    }

    /**
     * 查询第三方合理用药
     *
     * @param reqTO
     * @return
     */
    @RpcService
    public ThirdPartyRationalUseDrugResTO queryThirdPartyRationalUserDurg(ThirdPartyRationalUseDrugReqTO reqTO) {
        LOGGER.info("queryThirdPartyRationalUserDurg params: {}", JSONUtils.toString(reqTO));
        IRecipeHisService iRecipeHisService = AppContextHolder.getBean("his.iRecipeHisService", IRecipeHisService.class);
        HisResponseTO<ThirdPartyRationalUseDrugResTO> hisResponseTO = iRecipeHisService.queryThirdPartyRationalUserDurg(reqTO);
        LOGGER.info("queryThirdPartyRationalUserDurg result：{}, idCard: {}", JSONUtils.toString(hisResponseTO), reqTO.getThirdPartyPatientData().getIdCard());
        if (Objects.isNull(hisResponseTO)) {
            throw new DAOException("前置机调用失败");
        }
        return hisResponseTO.getData();
    }

    @RpcService
    public List<RecipeDetailBean> validateOfflineDrug(Integer organId, String mpiId, String recipeCode) {
        try {
            LOGGER.info("validateOfflineDrug organId={}, mpiId={},recipeCode={}", organId, mpiId, recipeCode);
            List<RecipeInfoTO> recipeInfoTO = recipeListInfos(organId, mpiId);
            if (CollectionUtils.isEmpty(recipeInfoTO)) {
                throw new DAOException(ErrorCode.SERVICE_ERROR, "获取处方数据为空");
            }
            RecipeInfoTO recipeInfo = recipeInfoTO.stream().filter(a -> recipeCode.equals(a.getRecipeCode())).findAny().orElse(null);
            if (null == recipeInfo) {
                throw new DAOException(ErrorCode.SERVICE_ERROR, "获取处方代码数据为空");
            }
            //获取线下处方药品
            List<RecipeDetailTO> detailData = recipeInfo.getDetailData();
            if (CollectionUtils.isEmpty(detailData)) {
                throw new DAOException(ErrorCode.SERVICE_ERROR, "获取药品数据为空");
            }
            List<String> drugCodes = detailData.stream().map(RecipeDetailTO::getDrugCode).distinct().collect(Collectors.toList());
            //获取药品比对信息
            List<OrganDrugList> organDrugs = organDrugListDAO.findByOrganIdAndDrugCodes(organId, drugCodes);
            if (CollectionUtils.isEmpty(organDrugs)) {
                throw new DAOException(ErrorCode.SERVICE_ERROR, "该处方单上的药品不在医院线上药品清单内，无法正常续方");
            }
            return offlineDrugs(organId, organDrugs, detailData);
        } catch (DAOException e) {
            throw new DAOException(e.getCode(), e.getMessage());
        } catch (Exception e1) {
            LOGGER.error("validateOfflineDrug error ", e1);
            throw new DAOException(ErrorCode.SERVICE_ERROR, e1.getMessage());
        }
    }

    /**
     * @param organId    机构id
     * @param organDrugs 机构药品
     * @param detailData 线下处方药品
     * @return
     */
    private List<RecipeDetailBean> offlineDrugs(Integer organId, List<OrganDrugList> organDrugs, List<RecipeDetailTO> detailData) {
        LOGGER.info("offlineDrugs organId = {},organDrugs={},detailData={}", organId, JSONUtils.toString(organDrugs), JSONUtils.toString(detailData));
        Map<String, OrganDrugList> organDrugMap = organDrugs.stream().collect(Collectors.toMap(OrganDrugList::getOrganDrugCode, a -> a, (k1, k2) -> k1));
        List<Integer> drugIds = organDrugs.stream().map(OrganDrugList::getDrugId).distinct().collect(Collectors.toList());
        List<DrugList> drugList = drugListDAO.findByDrugIds(drugIds);
        Map<Integer, DrugList> drugMap = drugList.stream().collect(Collectors.toMap(DrugList::getDrugId, a -> a, (k1, k2) -> k1));

        List<RecipeDetailBean> backDetailList = new ArrayList<>();
        StringBuilder msg = new StringBuilder();
        for (RecipeDetailTO recipeDetail : detailData) {
            /**线下处方 续方逻辑校验*/
            OrganDrugList organDrug = organDrugMap.get(recipeDetail.getDrugCode());
            if (null == organDrug) {
                continue;
            }

            if (null == recipeDetail.getAmount() || StringUtils.isAnyEmpty(recipeDetail.getUsePathwaysCode(), recipeDetail.getUsingRateCode(), recipeDetail.getUseDose(), recipeDetail.getUseDoseUnit(), recipeDetail.getDays())) {
                msg.append(recipeDetail.getDrugName()).append(",");
                continue;
            }
            StringBuilder str = new StringBuilder();
            UsingRateDTO usingRateDTO = usingRateService.findUsingRateDTOByOrganAndKey(organId, recipeDetail.getUsingRateCode());
            if (null == usingRateDTO) {
                LOGGER.warn("validateOfflineDrug usingRateDTO organId={} recipeDetailTO={}", organId, JSONUtils.toString(recipeDetail));
                str.append("用药频次");
            }

            UsePathwaysDTO usePathwaysDTO = usePathwaysService.findUsePathwaysByOrganAndKey(organDrug.getOrganId(), recipeDetail.getUsePathwaysCode());
            if (null == usePathwaysDTO) {
                LOGGER.warn("validateOfflineDrug usePathwaysDTO organId={} recipeDetailTO={}", organId, JSONUtils.toString(recipeDetail));
                if (StringUtils.isNotEmpty(str)) {
                    str.append("、");
                }
                str.append("用药途径");
            }
            if (StringUtils.isNotEmpty(str)) {
                throw new DAOException(ErrorCode.SERVICE_ERROR, "该医院" + str.toString() + "未在线上做维护，无法正常续方。");
            }

            //设置医生端每次剂量和剂量单位联动关系
            List<UseDoseAndUnitRelationBean> useDoseAndUnitRelationList = Lists.newArrayList();
            //用药单位不为空时才返回给前端
            if (StringUtils.isNotEmpty(organDrug.getUseDoseUnit())) {
                useDoseAndUnitRelationList.add(new UseDoseAndUnitRelationBean(organDrug.getRecommendedUseDose(), organDrug.getUseDoseUnit(), organDrug.getUseDose()));
            }
            if (StringUtils.isNotEmpty(organDrug.getUseDoseSmallestUnit())) {
                useDoseAndUnitRelationList.add(new UseDoseAndUnitRelationBean(organDrug.getDefaultSmallestUnitUseDose(), organDrug.getUseDoseSmallestUnit(), organDrug.getSmallestUnitUseDose()));
            }
            //组织处方药品续方参数
            RecipeDetailBean mapDetail = new RecipeDetailBean();
            mapDetail.setUsingRateId(String.valueOf(usingRateDTO.getId()));
            mapDetail.setUsePathwaysId(String.valueOf(usePathwaysDTO.getId()));
            mapDetail.setUseDoseAndUnitRelation(useDoseAndUnitRelationList);

            mapDetail.setDrugForm(organDrug.getDrugForm());
            mapDetail.setStatus(organDrug.getStatus());
            mapDetail.setDrugId(organDrug.getDrugId());

            DrugList drug = drugMap.get(organDrug.getDrugId());
            if (null != drug) {
                mapDetail.setDefaultUseDose(drug.getUseDose());
                mapDetail.setUsingRate(drug.getUsingRate());
                mapDetail.setUsePathways(drug.getUsePathways());
            }
            mapDetail.setDrugCost(recipeDetail.getTotalPrice());
            mapDetail.setDrugName(recipeDetail.getDrugName());
            mapDetail.setDrugSpec(recipeDetail.getDrugSpec());
            mapDetail.setOrganDrugCode(recipeDetail.getDrugCode());
            mapDetail.setOrganUsePathways(recipeDetail.getUsePathwaysCode());
            mapDetail.setOrganUsingRate(recipeDetail.getUsingRateCode());
            mapDetail.setPack(recipeDetail.getPack());
            mapDetail.setPrice(recipeDetail.getPrice());
            mapDetail.setRatePrice(recipeDetail.getPrice());
            mapDetail.setSalePrice(recipeDetail.getPrice());
            mapDetail.setSendNumber(recipeDetail.getAmount());
            mapDetail.setUseDays(recipeDetail.getUseDays());
            mapDetail.setDrugUnit(recipeDetail.getUnit());
            if (null == recipeDetail.getUseDays()) {
                mapDetail.setUseDays(Integer.valueOf(recipeDetail.getDays()));
            }
            if (StringUtils.isNotEmpty(recipeDetail.getUseDose())) {
                mapDetail.setUseDose(Double.valueOf(recipeDetail.getUseDose()));
            }
            mapDetail.setUseDoseStr(recipeDetail.getUseDose());
            mapDetail.setUseDoseUnit(recipeDetail.getUseDoseUnit());
            mapDetail.setUsePathwaysTextFromHis(recipeDetail.getUsePathwaysText());
            if (null != recipeDetail.getUseTotalDose()) {
                mapDetail.setUseTotalDose(recipeDetail.getUseTotalDose().doubleValue());
            }
            mapDetail.setUsingRateTextFromHis(recipeDetail.getUsingRateText());
            backDetailList.add(mapDetail);
        }
        if (CollectionUtils.isEmpty(backDetailList) && StringUtils.isNotEmpty(msg)) {
            String msgStr = msg.toString();
            msgStr = msgStr.substring(0, msgStr.length() - 1);
            throw new DAOException(ErrorCode.SERVICE_ERROR, msgStr + "药品信息不全，无法正常续方。");
        }
        LOGGER.info("offlineDrugs backDetailList = {}", JSONUtils.toString(backDetailList));
        return backDetailList;
    }

    /**
     * 获取线下处方
     *
     * @param organId 机构id
     * @param mpiId   患者id
     * @return
     */
    private List<RecipeInfoTO> recipeListInfos(Integer organId, String mpiId) {
        if (StringUtils.isEmpty(mpiId)) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "患者id为空");
        }
        PatientDTO patientDTO = patientService.get(mpiId);
        if (patientDTO == null) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "找不到该患者");
        }
        PatientBaseInfo patientBaseInfo = new PatientBaseInfo();
        RevisitExDTO consultExDTO = getConsultBean(null, organId, mpiId);
        if (null != consultExDTO) {
            patientBaseInfo.setPatientID(consultExDTO.getCardId());
            patientBaseInfo.setCardID(consultExDTO.getCardId());
            patientBaseInfo.setCardType(consultExDTO.getCardType());
        }
        patientBaseInfo.setPatientName(patientDTO.getPatientName());
        patientBaseInfo.setCertificate(patientDTO.getCertificate());
        patientBaseInfo.setCertificateType(patientDTO.getCertificateType());

        QueryRecipeRequestTO request = new QueryRecipeRequestTO();
        request.setPatientInfo(patientBaseInfo);
        request.setStartDate(DateConversion.getDateTimeDaysAgo(100));
        request.setEndDate(DateTime.now().toDate());
        request.setOrgan(organId);
        try {
            QueryRecipeResponseTO response = recipeHisService.queryRecipeListInfo(request);
            LOGGER.info("recipeListInfos request={}，response={}", JSONUtils.toString(request), JSONUtils.toString(response));
            if (null == response) {
                throw new DAOException(ErrorCode.SERVICE_ERROR, "查询数据为空");
            }
            return response.getData();
        } catch (Exception e) {
            LOGGER.error("recipeListInfos request={}，e", JSONUtils.toString(request), e);
            throw new DAOException(ErrorCode.SERVICE_ERROR, "查询数据出错");
        }
    }

    /**
     * 获取咨询信息
     *
     * @param consultId 咨询id
     * @param organId   机构id
     * @param mpiId     患者id
     * @return
     */
    public RevisitExDTO getConsultBean(Integer consultId, Integer organId, String mpiId) {
        if (null == organId) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "机构id 为空");
        }
        OrganService organService = ApplicationUtils.getBasicService(OrganService.class);
        OrganDTO organDTO = organService.getByOrganId(organId);
        if (organDTO == null) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "找不到该机构");
        }
        if (consultId == null) {
            List<RevisitBean> consultBeans = consultService.findConsultByMpiId(Collections.singletonList(mpiId));
            if (CollectionUtils.isNotEmpty(consultBeans)) {
                consultId = consultBeans.get(0).getConsultId();
            }
        }
        if (consultId == null) {
            return null;
        }
        RevisitExDTO consultExDTO = consultExService.getByConsultId(consultId);
        return consultExDTO;
    }

    /**
     * 查询用药记录列表
     *
     * @param organId
     * @param mpiId
     * @return
     */
    @RpcService
    public Map<String, Object> queryHisInsureRecipeList(Integer organId, String mpiId) {
        LOGGER.info("queryHisInsureRecipeList organId={},mpiId={}", organId, mpiId);
        Map<String, Object> response = queryHisInsureRecipeListFromHis(organId, mpiId);
        return response;
    }

    private Map<String, Object> queryHisInsureRecipeListFromHis(Integer organId, String mpiId) {
        LOGGER.info("queryHisInsureRecipeListFromHis  organId={},mpiId={}", organId, mpiId);
        PatientService patientService = ApplicationUtils.getBasicService(PatientService.class);
        HealthCardService healthCardService = ApplicationUtils.getBasicService(HealthCardService.class);
        Map<String, Object> result = Maps.newHashMap();
        PatientDTO patientDTO = patientService.get(mpiId);
        if (patientDTO == null) {
            throw new DAOException(609, "找不到该患者");
        }
        OrganService organService = ApplicationUtils.getBasicService(OrganService.class);
        OrganDTO organDTO = organService.getByOrganId(organId);
        if (organDTO == null) {
            throw new DAOException(609, "找不到该机构");
        }
        String cardId = null;
        String cardType = null;
        RevisitExDTO consultExDTO = getConsultBean(null, organId, mpiId);
        if (null != consultExDTO) {
            cardId = consultExDTO.getCardId();
            cardType = consultExDTO.getCardType();
        }
        Date endDate = DateTime.now().toDate();
        Date startDate = DateConversion.getDateTimeDaysAgo(90);

        IRecipeHisService hisService = AppDomainContext.getBean("his.iRecipeHisService", IRecipeHisService.class);
        QueryRecipeRequestTO request = new QueryRecipeRequestTO();
        PatientBaseInfo patientBaseInfo = new PatientBaseInfo();
        patientBaseInfo.setPatientName(patientDTO.getPatientName());
        patientBaseInfo.setPatientID(cardId);
        patientBaseInfo.setCertificate(patientDTO.getCertificate());
        patientBaseInfo.setCertificateType(patientDTO.getCertificateType());
        patientBaseInfo.setCardID(cardId);
        patientBaseInfo.setCardType(cardType);

        //杭州市互联网医院监管中心 管理单元eh3301
        OrganDTO organDTOCard = organService.getByManageUnit("eh3301");
        if (organDTOCard != null) {
            String cityCardNumber = healthCardService.getMedicareCardId(mpiId, organDTOCard.getOrganId());
            patientBaseInfo.setCityCardNumber(cityCardNumber);
        } else {
            LOGGER.info("queryHisInsureRecipeListFromHis 未获取到杭州市互联网医院监管中心机构");
        }

        request.setPatientInfo(patientBaseInfo);
        request.setStartDate(startDate);
        request.setEndDate(endDate);
        request.setOrgan(organId);

        LOGGER.info("queryHisInsureRecipeListFromHis request={}", JSONUtils.toString(request));
        QueryRecipeResponseTO response = null;
        try {
            response = hisService.queryHisInsureRecipeList(request);
        } catch (Exception e) {
            LOGGER.warn("getHosRecipeList his error. ", e);
        }
        LOGGER.info("queryHisInsureRecipeListFromHis res={}", JSONUtils.toString(response));
        if (null == response) {
            return result;
        }
        List<RecipeInfoTO> data = response.getData();
        //转换平台字段
        if (CollectionUtils.isEmpty(data)) {
            return result;
        }
        List<RecipeBean> recipes = Lists.newArrayList();
        for (RecipeInfoTO recipeInfoTO : data) {
            HisRecipeBean recipeBean = ObjectCopyUtils.convert(recipeInfoTO, HisRecipeBean.class);
            recipeBean.setSignDate(recipeInfoTO.getSignTime());
            recipeBean.setOrganDiseaseName(recipeInfoTO.getDiseaseName());
            recipeBean.setDepartText(recipeInfoTO.getDepartName());
            List<RecipeDetailTO> detailData = recipeInfoTO.getDetailData();
            List<HisRecipeDetailBean> hisRecipeDetailBeans = Lists.newArrayList();
            if (!CollectionUtils.isEmpty(detailData)) {
                for (RecipeDetailTO recipeDetailTO : detailData) {
                    HisRecipeDetailBean detailBean = ObjectCopyUtils.convert(recipeDetailTO, HisRecipeDetailBean.class);
                    detailBean.setDrugUnit(recipeDetailTO.getUnit());
                    detailBean.setUsingRateText(recipeDetailTO.getUsingRate());
                    detailBean.setUsePathwaysText(recipeDetailTO.getUsePathWays());
                    detailBean.setUseDays(recipeDetailTO.getDays());
                    detailBean.setUseTotalDose(recipeDetailTO.getAmount());
                    detailBean.setDrugSpec(recipeDetailTO.getDrugSpec());
                    hisRecipeDetailBeans.add(detailBean);
                }
            }
            recipeBean.setDetailData(hisRecipeDetailBeans);
            recipeBean.setClinicOrgan(organId);
            recipeBean.setOrganName(organDTO.getShortName());
            RecipeBean r = RecipeServiceSub.convertHisRecipeForRAP(recipeBean);
            recipes.add(r);

        }
        result.put("hisRecipe", recipes);
        result.put("patient", convertSensitivePatientForRAP(patientDTO));
        return result;
        //转换平台字段
    }

    /**
     * 查询用药记录详情
     *
     * @param organId
     * @param mpiId
     * @param recipeCode
     * @return
     */
    @RpcService
    public List<HisRecipeDetailBean> queryHisInsureRecipeInfo(Integer organId, String mpiId, String recipeCode, String serialNumber) {
        LOGGER.info("queryHisInsureRecipeInfo organId={},mpiId={},recipeCode={} ,serialNumber={}", organId, mpiId, recipeCode, serialNumber);
        List<HisRecipeDetailBean> response = queryHisInsureRecipeInfoFromHis(organId, mpiId, recipeCode, serialNumber);
        return response;
    }

    private List<HisRecipeDetailBean> queryHisInsureRecipeInfoFromHis(Integer organId, String mpiId, String recipeCode, String serialNumber) {
        List<HisRecipeDetailBean> recipeDetailTOs = new ArrayList<>();
        LOGGER.info("queryHisInsureRecipeInfoFromHis organId={},mpiId={},recipeCode={} ,serialNumber={}", organId, mpiId, recipeCode, serialNumber);
        PatientService patientService = ApplicationUtils.getBasicService(PatientService.class);
        HealthCardService healthCardService = ApplicationUtils.getBasicService(HealthCardService.class);
        Map<String, Object> result = Maps.newHashMap();
        PatientDTO patientDTO = patientService.get(mpiId);
        if (patientDTO == null) {
            throw new DAOException(609, "找不到该患者");
        }
        OrganService organService = ApplicationUtils.getBasicService(OrganService.class);
        OrganDTO organDTO = organService.getByOrganId(organId);
        if (organDTO == null) {
            throw new DAOException(609, "找不到该机构");
        }
        String cardId = null;
        String cardType = null;
        RevisitExDTO consultExDTO = getConsultBean(null, organId, mpiId);
        if (null != consultExDTO) {
            cardId = consultExDTO.getCardId();
            cardType = consultExDTO.getCardType();
        }


        IRecipeHisService hisService = AppDomainContext.getBean("his.iRecipeHisService", IRecipeHisService.class);
        QueryRecipeRequestTO request = new QueryRecipeRequestTO();
        PatientBaseInfo patientBaseInfo = new PatientBaseInfo();
        patientBaseInfo.setPatientName(patientDTO.getPatientName());
        patientBaseInfo.setPatientID(cardId);
        String cityCardNumber = healthCardService.getMedicareCardId(mpiId, organId);
        if (StringUtils.isNotEmpty(cityCardNumber)) {
            patientBaseInfo.setCityCardNumber(cityCardNumber);
        }
        request.setPatientInfo(patientBaseInfo);

        request.setOrgan(organId);
        request.setRecipeCode(recipeCode);
        request.setSerialNumber(serialNumber);
        LOGGER.info("queryHisInsureRecipeInfoFromHis request={}", JSONUtils.toString(request));
        HisResponseTO<List<RecipeDetailTO>> response = new HisResponseTO<>();
        try {
            response = hisService.queryHisInsureRecipeInfo(request);
        } catch (Exception e) {
            LOGGER.warn("getHosRecipeList his error. ", e);
        }
        LOGGER.info("queryHisInsureRecipeInfoFromHis res={}", JSONUtils.toString(response));

        List<RecipeDetailTO> data = response.getData();
        //转换平台字段
        if (CollectionUtils.isEmpty(data)) {
            return recipeDetailTOs;
        }

        for (RecipeDetailTO recipeDetailTO : data) {
            HisRecipeDetailBean detailBean = ObjectCopyUtils.convert(recipeDetailTO, HisRecipeDetailBean.class);
            detailBean.setDrugUnit(recipeDetailTO.getUnit());
            detailBean.setUsingRateText(recipeDetailTO.getUsingRate());
            detailBean.setUsePathwaysText(recipeDetailTO.getUsePathWays());
            detailBean.setUseDays(recipeDetailTO.getDays());
            detailBean.setUseTotalDose(recipeDetailTO.getAmount());
            detailBean.setDrugSpec(recipeDetailTO.getDrugSpec());
            recipeDetailTOs.add(detailBean);
        }
        return recipeDetailTOs;
    }
}
