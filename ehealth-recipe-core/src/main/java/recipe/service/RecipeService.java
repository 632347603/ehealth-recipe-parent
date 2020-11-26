package recipe.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.ngari.base.BaseAPI;
import com.ngari.base.department.service.IDepartmentService;
import com.ngari.base.esign.service.IESignBaseService;
import com.ngari.base.hisconfig.service.IHisConfigService;
import com.ngari.base.organconfig.service.IOrganConfigService;
import com.ngari.base.patient.model.DocIndexBean;
import com.ngari.base.patient.model.PatientBean;
import com.ngari.base.patient.service.IPatientService;
import com.ngari.base.payment.service.IPaymentService;
import com.ngari.base.property.service.IConfigurationCenterUtilsService;
import com.ngari.base.push.model.SmsInfoBean;
import com.ngari.base.push.service.ISmsPushService;
import com.ngari.consult.ConsultAPI;
import com.ngari.consult.common.service.IConsultService;
import com.ngari.consult.process.service.IRecipeOnLineConsultService;
import com.ngari.his.ca.model.CaSealRequestTO;
import com.ngari.his.recipe.mode.DrugInfoTO;
import com.ngari.home.asyn.model.BussCancelEvent;
import com.ngari.home.asyn.model.BussFinishEvent;
import com.ngari.home.asyn.service.IAsynDoBussService;
import com.ngari.patient.ds.PatientDS;
import com.ngari.patient.dto.*;
import com.ngari.patient.service.*;
import com.ngari.patient.utils.ObjectCopyUtils;
import com.ngari.recipe.basic.ds.PatientVO;
import com.ngari.recipe.common.RecipeResultBean;
import com.ngari.recipe.drugsenterprise.model.RecipeLabelVO;
import com.ngari.recipe.entity.*;
import com.ngari.recipe.entity.sign.SignDoctorRecipeInfo;
import com.ngari.recipe.hisprescription.model.HospitalRecipeDTO;
import com.ngari.recipe.recipe.model.*;
import com.ngari.recipe.recipeorder.model.RecipeOrderBean;
import com.ngari.recipe.recipeorder.model.RecipeOrderInfoBean;
import com.ngari.revisit.RevisitAPI;
import com.ngari.revisit.common.service.IRevisitService;
import com.ngari.revisit.process.service.IRecipeOnLineRevisitService;
import com.ngari.wxpay.service.INgariRefundService;
import ctd.controller.exception.ControllerException;
import ctd.dictionary.DictionaryController;
import ctd.persistence.DAOFactory;
import static ctd.persistence.DAOFactory.getDAO;
import ctd.persistence.exception.DAOException;
import ctd.schema.exception.ValidateException;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcBean;
import ctd.util.annotation.RpcService;
import ctd.util.event.GlobalEventExecFactory;
import eh.base.constant.ErrorCode;
import eh.base.constant.PageConstant;
import eh.cdr.constant.OrderStatusConstant;
import eh.recipeaudit.api.IRecipeCheckDetailService;
import eh.recipeaudit.api.IRecipeCheckService;
import eh.recipeaudit.model.AuditMedicinesBean;
import eh.recipeaudit.model.Intelligent.AutoAuditResultBean;
import eh.recipeaudit.model.Intelligent.IssueBean;
import eh.recipeaudit.model.Intelligent.PAWebMedicinesBean;
import eh.recipeaudit.model.RecipeCheckBean;
import eh.recipeaudit.model.RecipeCheckDetailBean;
import eh.recipeaudit.util.RecipeAuditAPI;
import eh.utils.params.ParamUtils;
import eh.utils.params.ParameterConstant;
import eh.wxpay.constant.PayConstant;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.util.Args;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import recipe.ApplicationUtils;
import recipe.audit.auditmode.AuditModeContext;
import recipe.audit.service.PrescriptionService;
import recipe.bean.CheckYsInfoBean;
import recipe.bean.DrugEnterpriseResult;
import recipe.bussutil.CreateRecipePdfUtil;
import recipe.bussutil.RecipeUtil;
import recipe.bussutil.RecipeValidateUtil;
import recipe.ca.CAInterface;
import recipe.ca.factory.CommonCAFactory;
import recipe.ca.vo.CaSignResultVo;
import recipe.caNew.AbstractCaProcessType;
import recipe.caNew.CaAfterProcessType;
import recipe.common.CommonConstant;
import recipe.common.response.CommonResponse;
import recipe.constant.*;
import recipe.dao.*;
import recipe.dao.bean.PatientRecipeBean;
import recipe.drugsenterprise.*;
import recipe.drugsenterprise.bean.YdUrlPatient;
import recipe.hisservice.RecipeToHisCallbackService;
import recipe.hisservice.syncdata.HisSyncSupervisionService;
import recipe.hisservice.syncdata.SyncExecutorService;
import recipe.purchase.PurchaseService;
import recipe.service.common.RecipeCacheService;
import recipe.service.common.RecipeSignService;
import recipe.service.manager.EmrRecipeManager;
import recipe.service.manager.RecipeLabelManager;
import recipe.sign.SignRecipeInfoService;
import recipe.thread.*;
import recipe.util.*;
import video.ainemo.server.IVideoInfoService;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * 处方服务类
 * company: ngarihealth
 *
 * @author: 0184/yu_yun
 * @date:2016/4/27.
 */
@RpcBean("recipeService")
public class RecipeService extends RecipeBaseService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RecipeService.class);

    private static final String EXTEND_VALUE_FLAG = "1";

    private static final Integer CA_OLD_TYPE = new Integer(0);

    private static final Integer CA_NEW_TYPE = new Integer(1);

    private PatientService patientService = ApplicationUtils.getBasicService(PatientService.class);

    private DoctorService doctorService = ApplicationUtils.getBasicService(DoctorService.class);

    private static IPatientService iPatientService = ApplicationUtils.getBaseService(IPatientService.class);

    private RecipeCacheService cacheService = ApplicationUtils.getRecipeService(RecipeCacheService.class);

    private static final int havChooseFlag = 1;
    @Autowired
    private RedisClient redisClient;

    @Resource
    private AuditModeContext auditModeContext;

    @Resource
    private OrganDrugListDAO organDrugListDAO;

    @Autowired
    private SignRecipeInfoService signRecipeInfoService;

    @Autowired
    private CommonCAFactory commonCAFactory;

    @Autowired
    private IConfigurationCenterUtilsService configService;

    @Autowired
    private RecipeLabelManager recipeLabelManager;

    @Autowired
    private PharmacyTcmDAO pharmacyTcmDAO;
    @Autowired
    private RecipeServiceSub recipeServiceSub;
    @Autowired
    private EmrRecipeManager emrRecipeManager;
    @Autowired
    private RecipeExtendDAO recipeExtendDAO;

    @Autowired
    private RecipeDAO recipeDAO;

    @Resource
    private CaAfterProcessType caAfterProcessType;

    /**
     * 药师审核不通过
     */
    public static final int CHECK_NOT_PASS = 2;
    /**
     * 推送药企失败
     */
    public static final int PUSH_FAIL = 3;
    /**
     * 手动退款
     */
    public static final int REFUND_MANUALLY = 4;
    /**
     * 患者手动退款
     */
    public static final int REFUND_PATIENT = 5;

    public static final String WX_RECIPE_BUSTYPE = "recipe";

    public static final Integer RECIPE_EXPIRED_DAYS = 3;

    /**
     * 二次签名处方审核不通过过期时间
     */
    public static final Integer RECIPE_EXPIRED_SECTION = 30;

    /**
     * 过期处方查询起始天数
     */
    public static final Integer RECIPE_EXPIRED_SEARCH_DAYS = 13;

    /*处方中药标识*/
    private static final String TCM_TEMPLATETYPE = "tcm";

    @RpcService
    public RecipeBean getByRecipeId(int recipeId) {
        Recipe recipe = DAOFactory.getDAO(RecipeDAO.class).get(recipeId);
        return ObjectCopyUtils.convert(recipe, RecipeBean.class);
    }

    @RpcService
    public List<RecipeBean> findRecipe(int start, int limit) {
        List<Recipe> recipes = DAOFactory.getDAO(RecipeDAO.class).findRecipeByStartAndLimit(start, limit);
        return ObjectCopyUtils.convert(recipes, RecipeBean.class);
    }

    /**
     * 复诊页面点击开处方
     * 判断视频问诊后才能开具处方 并且视频大于30s
     */
    @RpcService
    public void openRecipeOrNotForVideo(CanOpenRecipeReqDTO req) {
        Args.notNull(req.getOrganId(), "organId");
        Args.notNull(req.getClinicID(), "clinicID");
        Boolean openRecipeOrNotForVideo = false;
        try {
            IConfigurationCenterUtilsService configurationCenterUtilsService = ApplicationUtils.getBaseService(IConfigurationCenterUtilsService.class);
            openRecipeOrNotForVideo = (Boolean) configurationCenterUtilsService.getConfiguration(req.getOrganId(), "openRecipeOrNotForVideo");
        } catch (Exception e) {
            LOGGER.error("openRecipeOrNotForVideo error", e);
        }
        if (openRecipeOrNotForVideo) {
            IVideoInfoService videoInfoService = AppContextHolder.getBean("video.videoInfoService", IVideoInfoService.class);
            //字典eh.bus.dictionary.VideoBussType
            Boolean canVideo = videoInfoService.haveVideoByIdAndTime(req.getClinicID(), 35, 30);
            if (!canVideo) {
                throw new DAOException(609, "您与患者的视频未达到医院规定时长，无法开具处方。若达到时长请稍后再次尝试开具处方");
            }
        }
    }

    /**
     * 判断医生是否可以处方
     *
     * @param doctorId 医生ID
     * @return Map<String, Object>
     */
    @RpcService
    public Map<String, Object> openRecipeOrNot(Integer doctorId) {
        EmploymentService employmentService = ApplicationUtils.getBasicService(EmploymentService.class);
        ConsultSetService consultSetService = ApplicationUtils.getBasicService(ConsultSetService.class);

        Boolean canCreateRecipe = false;
        String tips = "";
        Map<String, Object> map = Maps.newHashMap();
        List<EmploymentDTO> employmentList = employmentService.findEmploymentByDoctorId(doctorId);
        List<Integer> organIdList = new ArrayList<>();
        if (employmentList.size() > 0) {
            for (EmploymentDTO employment : employmentList) {
                organIdList.add(employment.getOrganId());
            }
            OrganDrugListDAO organDrugListDAO = getDAO(OrganDrugListDAO.class);
            int listNum = organDrugListDAO.getCountByOrganIdAndStatus(organIdList);
            canCreateRecipe = listNum > 0;
            if (!canCreateRecipe) {
                tips = "抱歉，您所在医院暂不支持开处方业务。";
            }
        }

        //能否开医保处方
        boolean medicalFlag = false;
        if (canCreateRecipe) {
            ConsultSetDTO set = consultSetService.getBeanByDoctorId(doctorId);
            if (null != set && null != set.getMedicarePrescription()) {
                medicalFlag = (true == set.getMedicarePrescription()) ? true : false;
            }
        }

        map.put("result", canCreateRecipe);
        map.put("medicalFlag", medicalFlag);
        map.put("tips", tips);
        return map;

    }

    /**
     * 新的处方列表  pc端仍在使用
     *
     * @param doctorId 医生ID
     * @param start    记录开始下标
     * @param limit    每页限制条数
     * @return list
     */
    @RpcService
    public List<HashMap<String, Object>> findNewRecipeAndPatient(int doctorId, int start, int limit) {
        checkUserHasPermissionByDoctorId(doctorId);
        return RecipeServiceSub.findRecipesAndPatientsByDoctor(doctorId, start, PageConstant.getPageLimit(limit), 0);
    }

    /**
     * 历史处方列表 pc端仍在使用
     *
     * @param doctorId 医生ID
     * @param start    记录开始下标
     * @param limit    每页限制条数
     * @return list
     */
    @RpcService
    public List<HashMap<String, Object>> findOldRecipeAndPatient(int doctorId, int start, int limit) {
        checkUserHasPermissionByDoctorId(doctorId);
        return RecipeServiceSub.findRecipesAndPatientsByDoctor(doctorId, start, PageConstant.getPageLimit(limit), 1);
    }

    /**
     * 强制删除处方(接收医院处方发送失败时处理)
     *
     * @param recipeId 处方ID
     * @return boolean
     */
    @RpcService
    public Boolean delRecipeForce(int recipeId) {
        LOGGER.info("delRecipeForce [recipeId:" + recipeId + "]");
        RecipeDAO recipeDAO = getDAO(RecipeDAO.class);
        RecipeExtendDAO recipeExtendDAO = getDAO(RecipeExtendDAO.class);
        recipeDAO.remove(recipeId);
        recipeExtendDAO.remove(recipeId);
        return true;
    }

    /**
     * 删除处方
     *
     * @param recipeId 处方ID
     * @return boolean
     */
    @RpcService
    public Boolean delRecipe(int recipeId) {
        LOGGER.info("delRecipe [recipeId:" + recipeId + "]");
        RecipeDAO recipeDAO = getDAO(RecipeDAO.class);
        Recipe recipe = recipeDAO.getByRecipeId(recipeId);
        if (null == recipe) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "该处方单不存在或者已删除");
        }
        if (null == recipe.getStatus() || recipe.getStatus() > RecipeStatusConstant.UNSIGN) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "该处方单不是新处方或者审核失败的处方，不能删除");
        }

        boolean rs = recipeDAO.updateRecipeInfoByRecipeId(recipeId, RecipeStatusConstant.DELETE, null);

        //记录日志
        RecipeLogService.saveRecipeLog(recipeId, recipe.getStatus(), RecipeStatusConstant.DELETE, "删除处方单");

        return rs;
    }

    /**
     * 撤销处方单
     *
     * @param recipeId 处方ID
     * @return boolean
     */
    @RpcService
    public Boolean undoRecipe(int recipeId) {
        LOGGER.info("undoRecipe [recipeId：" + recipeId + "]");
        RecipeDAO recipeDAO = getDAO(RecipeDAO.class);
        Recipe recipe = recipeDAO.getByRecipeId(recipeId);
        if (null == recipe) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "该处方单不存在或者已删除");
        }
        if (null == recipe.getStatus() || RecipeStatusConstant.UNCHECK != recipe.getStatus()) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "该处方单不是待审核的处方，不能撤销");
        }

        boolean rs = recipeDAO.updateRecipeInfoByRecipeId(recipeId, RecipeStatusConstant.UNSIGN, null);

        //记录日志
        RecipeLogService.saveRecipeLog(recipeId, recipe.getStatus(), RecipeStatusConstant.UNSIGN, "撤销处方单");

        return rs;
    }

    /**
     * 保存处方
     *
     * @param recipeBean     处方对象
     * @param detailBeanList 处方详情
     * @return int
     */
    @RpcService
    public Integer saveRecipeData(RecipeBean recipeBean, List<RecipeDetailBean> detailBeanList) {
        Integer recipeId = recipeServiceSub.saveRecipeDataImpl(recipeBean, detailBeanList, 1);
        if (RecipeBussConstant.FROMFLAG_HIS_USE.equals(recipeBean.getFromflag())) {
            //生成订单数据，与 HosPrescriptionService 中 createPrescription 方法一致
            HosPrescriptionService service = AppContextHolder.getBean("hosPrescriptionService", HosPrescriptionService.class);
            recipeBean.setRecipeId(recipeId);
            //设置订单基本参数
            HospitalRecipeDTO hospitalRecipeDTO = new HospitalRecipeDTO();
            hospitalRecipeDTO.setRecipeCode(recipeBean.getRecipeCode());
            hospitalRecipeDTO.setOrderTotalFee(recipeBean.getTotalMoney().toPlainString());
            hospitalRecipeDTO.setActualFee(hospitalRecipeDTO.getOrderTotalFee());
            recipeBean.setPayFlag(PayConstant.PAY_FLAG_NOT_PAY);
            service.createBlankOrderForHos(recipeBean, hospitalRecipeDTO);
        }
        return recipeId;
    }

    /**
     * 保存HIS处方
     *
     * @param recipe
     * @param details
     * @return
     */
    public Integer saveRecipeDataForHos(RecipeBean recipe, List<RecipeDetailBean> details) {
        return recipeServiceSub.saveRecipeDataImpl(recipe, details, 0);
    }

    /**
     * 保存处方电子病历
     *
     * @param recipe 处方对象
     */
    public void saveRecipeDocIndex(Recipe recipe) {
        IDepartmentService iDepartmentService = ApplicationUtils.getBaseService(IDepartmentService.class);

        DocIndexBean docIndex = new DocIndexBean();
        String docType = "3";
        try {
            String docTypeText = DictionaryController.instance().get("eh.cdr.dictionary.DocType").getText(docType);
            docIndex.setDocSummary(docTypeText);
            docIndex.setDoctypeName(docTypeText);
        } catch (ControllerException e) {
            LOGGER.error("saveRecipeDocIndex DocType dictionary error! docType=", docType, e);
        }
        try {
            String recipeTypeText = DictionaryController.instance().get("eh.cdr.dictionary.RecipeType").getText(recipe.getRecipeType());
            docIndex.setDocTitle(recipeTypeText);
        } catch (ControllerException e) {
            LOGGER.error("saveRecipeDocIndex RecipeType dictionary error! recipeType=", recipe.getRecipeType(), e);
        }
        docIndex.setDocId(recipe.getRecipeId());
        docIndex.setMpiid(recipe.getMpiid());
        docIndex.setCreateOrgan(recipe.getClinicOrgan());
        docIndex.setCreateDepart(recipe.getDepart());
        docIndex.setCreateDoctor(recipe.getDoctor());
        docIndex.setDoctorName(doctorService.getNameById(recipe.getDoctor()));
        docIndex.setDepartName(iDepartmentService.getNameById(recipe.getDepart()));
        iPatientService.saveRecipeDocIndex(docIndex, docType, 3);
    }

    /**
     * 根据处方ID获取完整地址
     *
     * @param recipeId
     * @return
     */
    @RpcService
    public String getCompleteAddress(Integer recipeId) {
        String address = "";
        if (null != recipeId) {
            CommonRemoteService commonRemoteService = AppContextHolder.getBean("commonRemoteService", CommonRemoteService.class);
            RecipeDAO recipeDAO = getDAO(RecipeDAO.class);

            Recipe recipe = recipeDAO.get(recipeId);
            if (null != recipe) {
                if (null != recipe.getAddressId()) {
                    StringBuilder sb = new StringBuilder();
                    commonRemoteService.getAddressDic(sb, recipe.getAddress1());
                    commonRemoteService.getAddressDic(sb, recipe.getAddress2());
                    commonRemoteService.getAddressDic(sb, recipe.getAddress3());
                    sb.append(StringUtils.isEmpty(recipe.getAddress4()) ? "" : recipe.getAddress4());
                    address = sb.toString();
                }

                if (StringUtils.isEmpty(address)) {
                    RecipeOrderDAO recipeOrderDAO = getDAO(RecipeOrderDAO.class);
                    //从订单获取
                    RecipeOrder order = recipeOrderDAO.getOrderByRecipeId(recipeId);
                    if (null != order && null != order.getAddressID()) {
                        address = commonRemoteService.getCompleteAddress(order);
                    }
                }
            }
        }

        return address;
    }



    public RecipeResultBean generateCheckRecipePdf(Integer checker, Recipe recipe, int beforeStatus, int recipeStatus) {
        RecipeResultBean checkResult = RecipeResultBean.getSuccess();
        RecipeDAO recipeDAO = getDAO(RecipeDAO.class);
        DoctorService doctorService = BasicAPI.getService(DoctorService.class);

        boolean bl = false;
        Integer recipeId = recipe.getRecipeId();
        String errorMsg = "";
        if (null != recipe.getSignFile() || StringUtils.isNotEmpty(recipe.getSignRecipeCode())) {
            IESignBaseService esignService = ApplicationUtils.getBaseService(IESignBaseService.class);

            Map<String, Object> dataMap = Maps.newHashMap();
            dataMap.put("fileName", "recipecheck_" + recipeId + ".pdf");
            dataMap.put("recipeSignFileId", recipe.getSignFile());
            if (RecipeUtil.isTcmType(recipe.getRecipeType())) {
                dataMap.put("templateType", "tcm");
            } else {
                dataMap.put("templateType", "wm");
            }
            // 添加机构id
            dataMap.put("organId", recipe.getClinicOrgan());
            Object footerRemark = configService.getConfiguration(recipe.getClinicOrgan(), "recipeDetailRemark");
            if (null != footerRemark) {
                dataMap.put("footerRemark", footerRemark.toString());
            }
            Map<String, Object> backMap = esignService.signForRecipe(false, checker, dataMap);
            LOGGER.info("reviewRecipe  esignService backMap:{} ,e=============", JSONUtils.toString(backMap));
            //0表示成功
            Integer code = MapValueUtil.getInteger(backMap, "code");
            if (Integer.valueOf(0).equals(code)) {
                String recipeFileId = MapValueUtil.getString(backMap, "fileId");
                bl = recipeDAO.updateRecipeInfoByRecipeId(recipeId, ImmutableMap.<String, Object>of("chemistSignFile", recipeFileId));
            } else if (Integer.valueOf(2).equals(code)) {
                LOGGER.info("reviewRecipe 签名成功. 高州CA模式-全SDK对接模式, recipeId={}", recipe.getRecipeId());
                bl = true;
            } else if (Integer.valueOf(100).equals(code)) {
                LOGGER.info("reviewRecipe 签名成功. 标准对接CA模式-全后台对接模式, recipeId={}", recipe.getRecipeId());
                try {
                    String loginId = MapValueUtil.getString(backMap, "loginId");
                    Integer organId = recipe.getClinicOrgan();
                    DoctorDTO doctorDTOn = doctorService.getByDoctorId(recipe.getDoctor());
                    String userAccount = doctorDTOn.getIdNumber();
                    String caPassword = "";
                    //签名时的密码从redis中获取
                    if (null != redisClient.get("caPassword")) {
                        caPassword = redisClient.get("caPassword");
                    }
                    //标准化CA进行签名、签章==========================start=====
                    //获取签章pdf数据。签名原文
                    CaSealRequestTO requestSealTO = RecipeServiceEsignExt.signCreateRecipePDF(recipeId, false);
                    //获取签章图片
                    DoctorExtendService doctorExtendService = BasicAPI.getService(DoctorExtendService.class);
                    //date 20200601
                    //修改审方签名信息为药师
                    DoctorExtendDTO doctorExtendDTO = doctorExtendService.getByDoctorId(checker);
                    if (doctorExtendDTO != null && doctorExtendDTO.getSealData() != null) {
                        requestSealTO.setSealBase64Str(doctorExtendDTO.getSealData());
                    } else {
                        requestSealTO.setSealBase64Str("");
                    }
                    CommonCAFactory caFactory = new CommonCAFactory();
                    //通过工厂获取对应的实现CA类
                    CAInterface caInterface = commonCAFactory.useCAFunction(organId);
                    CaSignResultVo resultVo = caInterface.commonCASignAndSeal(requestSealTO, recipe, organId, userAccount, caPassword);
                    //date 20200618
                    //修改标准ca成异步操作，原先逻辑不做任何处理，抽出单独的异步实现接口
                    checkResult.setCode(RecipeResultBean.NO_ADDRESS);
                    return checkResult;

                } catch (Exception e) {
                    LOGGER.error("reviewRecipe  signFile 标准化CA签章报错 recipeId={} ,doctor={} ,e=============", recipeId, recipe.getDoctor(), e);
                    bl = false;
                    checkResult.setCode(RecipeResultBean.FAIL);
                }
                //标准化CA进行签名、签章==========================end=====
            } else {
                LOGGER.error("reviewRecipe signFile error. recipeId={}, result={}", recipeId, JSONUtils.toString(backMap));
                errorMsg = JSONUtils.toString(backMap);
                bl = false;
            }
        } else {
            LOGGER.error("reviewRecipe signFile is empty recipeId=" + recipeId);
            errorMsg = "signFileId is empty. recipeId=" + recipeId;
            bl = false;
        }

        if (!bl) {
            RecipeLogService.saveRecipeLog(recipeId, beforeStatus, recipeStatus, "reviewRecipe 添加药师签名失败. " + errorMsg);
        }
        //date 20200511
        //这里设置结果值，是由于原先方法也取这个结果集作为返回map中的result
        //当时当前方法不会根据签名和pdf的记过作为签名的结果所以用另一个字段记录结果延续下去
        checkResult.setObject(bl);
        //审核通过盖章
        RecipeBusiThreadPool.execute(new GenerateSignetRecipePdfRunable(recipe.getRecipeId(), recipe.getClinicOrgan()));
        return checkResult;
    }



    /**
     * 药师审核不通过的情况下，医生重新开处方
     *
     * @param recipeId
     * @return
     */
    @RpcService
    public List<RecipeDetailBean> reCreatedRecipe(Integer recipeId) {
        RecipeResultBean resultBean = RecipeResultBean.getSuccess();
        Recipe dbRecipe = RecipeValidateUtil.checkRecipeCommonInfo(recipeId, resultBean);
        if (null == dbRecipe) {
            LOGGER.error("reCreatedRecipe 平台无该处方对象. recipeId=[{}] error={}", recipeId, JSONUtils.toString(resultBean));
            return Lists.newArrayList();
        }
        Integer status = dbRecipe.getStatus();
        if (null == status || status != RecipeStatusConstant.CHECK_NOT_PASS_YS) {
            LOGGER.error("reCreatedRecipe 该处方不是审核未通过的处方. recipeId=[{}]", recipeId);
            return Lists.newArrayList();
        }
        //date 2020/1/2
        //发送二次不通过消息判断是否是二次审核不通过
        if (!RecipecCheckStatusConstant.Check_Normal.equals(dbRecipe.getCheckStatus())) {
            //添加发送不通过消息
            RecipeMsgService.batchSendMsg(dbRecipe, RecipeStatusConstant.CHECK_NOT_PASSYS_REACHPAY);
            //更新处方一次审核不通过标记
            RecipeDAO recipeDAO = getDAO(RecipeDAO.class);
            Map<String, Object> updateMap = new HashMap<>();
            updateMap.put("checkStatus", RecipecCheckStatusConstant.Check_Normal);
            recipeDAO.updateRecipeInfoByRecipeId(recipeId, updateMap);
            //HIS消息发送
            //审核不通过 往his更新状态（已取消）
            Recipe recipe = recipeDAO.getByRecipeId(recipeId);
            RecipeHisService hisService = ApplicationUtils.getRecipeService(RecipeHisService.class);
            hisService.recipeStatusUpdate(recipe.getRecipeId());
            //记录日志
            RecipeLogService.saveRecipeLog(recipe.getRecipeId(), recipe.getStatus(), recipe.getStatus(), "审核不通过处理完成");
        }

        //患者如果使用优惠券将优惠券解锁
        RecipeCouponService recipeCouponService = ApplicationUtils.getRecipeService(RecipeCouponService.class);
        recipeCouponService.unuseCouponByRecipeId(recipeId);

        //根据审方模式改变--审核未通过处理
        auditModeContext.getAuditModes(dbRecipe.getReviewType()).afterCheckNotPassYs(dbRecipe);
        List<RecipeDetailBean> detailBeanList = RecipeValidateUtil.validateDrugsImpl(dbRecipe);
        return detailBeanList;
    }

    /**
     * 重新开具 或这续方时校验 药品数据
     *
     * @param recipeId
     * @return
     */
    @RpcService
    public List<RecipeDetailBean> validateDrugs(Integer recipeId) {
        RecipeResultBean resultBean = RecipeResultBean.getSuccess();
        Recipe dbRecipe = RecipeValidateUtil.checkRecipeCommonInfo(recipeId, resultBean);
        if (null == dbRecipe) {
            LOGGER.error("validateDrugs 平台无该处方对象. recipeId=[{}] error={}", recipeId, JSONUtils.toString(resultBean));
            return Lists.newArrayList();
        }
        List<RecipeDetailBean> detailBeans = RecipeValidateUtil.validateDrugsImpl(dbRecipe);
        return detailBeans;
    }

    /**
     * 重新开具 或这续方时校验 药品数据---new校验接口，原接口保留-app端有对validateDrugs单独处理
     * 还有暂存的处方点进来时做药房配置的判断
     * @param recipeId
     * @return
     */
    @RpcService
    public void validateDrugsData(Integer recipeId) {
        RecipeDetailDAO detailDAO = DAOFactory.getDAO(RecipeDetailDAO.class);
        RecipeResultBean resultBean = RecipeResultBean.getSuccess();
        Recipe dbRecipe = RecipeValidateUtil.checkRecipeCommonInfo(recipeId, resultBean);
        if (null == dbRecipe) {
            LOGGER.error("validateDrugsData 平台无该处方对象. recipeId=[{}] ", recipeId);
            throw new DAOException(609,"获取不到处方数据");
        }
        List<Recipedetail> details = detailDAO.findByRecipeId(recipeId);
        if (CollectionUtils.isEmpty(details)) {
           return;
        }
        List<RecipeDetailBean> detailBeans = ObjectCopyUtils.convert(details, RecipeDetailBean.class);
        //药房配置校验
        if (CollectionUtils.isNotEmpty(detailBeans)){
            List<PharmacyTcm> pharmacyTcms = pharmacyTcmDAO.findByOrganId(dbRecipe.getClinicOrgan());
            if (CollectionUtils.isNotEmpty(pharmacyTcms)){
                List<Integer> pharmacyIdList = pharmacyTcms.stream().map(PharmacyTcm::getPharmacyId).collect(Collectors.toList());
                OrganDrugList organDrugList;
                for (RecipeDetailBean recipedetail : detailBeans) {
                    if (recipedetail.getPharmacyId() == null || recipedetail.getPharmacyId() == 0){
                        throw new DAOException(609,"您所在的机构已更新药房配置，需要重新开具处方");
                    }
                    //判断药房机构库配置
                    if (!pharmacyIdList.contains(recipedetail.getPharmacyId())){
                        throw new DAOException(609,"您所在的机构已更新药房配置，需要重新开具处方");
                    }
                    //判断药品归属药房
                    organDrugList = organDrugListDAO.getByOrganIdAndOrganDrugCodeAndDrugId(dbRecipe.getClinicOrgan(), recipedetail.getOrganDrugCode(), recipedetail.getDrugId());
                    if (organDrugList !=null){
                        if (StringUtils.isNotEmpty(organDrugList.getPharmacy())){
                            List<String> pharmacyIds = Splitter.on(",").splitToList(organDrugList.getPharmacy());
                            if (!pharmacyIds.contains(String.valueOf(recipedetail.getPharmacyId()))){
                                throw new DAOException(609,"您所在的机构已更新药房配置，需要重新开具处方");
                            }
                        }else {
                            throw new DAOException(609,"您所在的机构已更新药房配置，需要重新开具处方");
                        }

                    }
                }
            }

        }
    }

    /**
     * 生成pdf并签名
     *
     * @param recipeId
     */
    @RpcService
    public RecipeResultBean generateRecipePdfAndSign(Integer recipeId) {
        RecipeResultBean result = RecipeResultBean.getSuccess();
        if (null == recipeId) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "recipeId is null");
        }
        RecipeDAO recipeDAO = getDAO(RecipeDAO.class);
        RecipeDetailDAO detailDAO = getDAO(RecipeDetailDAO.class);
        IESignBaseService esignService = ApplicationUtils.getBaseService(IESignBaseService.class);

        Recipe recipe = recipeDAO.getByRecipeId(recipeId);
        List<Recipedetail> details = detailDAO.findByRecipeId(recipeId);

        //组装生成pdf的参数
        String fileName = "recipe_" + recipeId + ".pdf";
        Map<String, Object> paramMap = Maps.newHashMap();
        recipe.setSignDate(DateTime.now().toDate());
        if (RecipeUtil.isTcmType(recipe.getRecipeType())) {
            //中药pdf参数
            paramMap = RecipeServiceSub.createParamMapForChineseMedicine(recipe, details, fileName);
        } else {
            paramMap = RecipeServiceSub.createParamMap(recipe, details, fileName);
            //上传处方图片
            generateRecipeImageAndUpload(recipeId, paramMap);
        }
        //上传阿里云
        String memo = "";
        Object footerRemark = configService.getConfiguration(recipe.getClinicOrgan(), "recipeDetailRemark");
        if (null != footerRemark) {
            paramMap.put("footerRemark", footerRemark.toString());
        }
        Map<String, Object> backMap = esignService.signForRecipe(true, recipe.getDoctor(), paramMap);
        String imgFileId = MapValueUtil.getString(backMap, "imgFileId");
        Map<String, Object> attrMapimg = Maps.newHashMap();
        attrMapimg.put("signImg", imgFileId);
//        attrMapimg.put("Status", RecipeStatusConstant.SIGN_ING_CODE_DOC);
        recipeDAO.updateRecipeInfoByRecipeId(recipeId, attrMapimg);
        LOGGER.info("generateRecipeImg 签名图片成功. fileId={}, recipeId={}", imgFileId, recipe.getRecipeId());
        //0表示成功
        Integer code = MapValueUtil.getInteger(backMap, "code");
        if (Integer.valueOf(0).equals(code)) {
            String recipeFileId = MapValueUtil.getString(backMap, "fileId");
            Map<String, Object> attrMap = Maps.newHashMap();
            attrMap.put("signFile", recipeFileId);
            attrMap.put("signDate", recipe.getSignDate());
            recipeDAO.updateRecipeInfoByRecipeId(recipeId, attrMap);
            memo = "签名上传文件成功, fileId=" + recipeFileId;
            LOGGER.info("generateRecipePdfAndSign 签名成功. fileId={}, recipeId={}", recipeFileId, recipe.getRecipeId());
            //个性化医院处方笺生成条形码
            Set<String> organIdList = redisClient.sMembers(CacheConstant.KEY_BARCODEFORRECIPE_ORGAN_LIST);
            if (CollectionUtils.isNotEmpty(organIdList) && organIdList.contains(String.valueOf(recipe.getClinicOrgan()))) {
                RecipeBusiThreadPool.execute(() -> generateBarCodeForRecipePdfAndSwap(recipeId, recipeFileId, recipe.getRecipeCode()));
            }
        } else if (Integer.valueOf(2).equals(code)) {
            memo = "签名成功,高州CA方式";
            doctorToRecipePDF(recipeId, recipe);
            LOGGER.info("generateRecipePdfAndSign 签名成功. 高州CA模式, recipeId={}", recipe.getRecipeId());
        } else if (Integer.valueOf(100).equals(code)) {
            memo = "签名成功,标准对接CA方式";
            doctorToRecipePDF(recipeId, recipe);
            LOGGER.info("generateRecipePdfAndSign 签名成功. 标准对接CA模式, recipeId={}", recipe.getRecipeId());
            try {
                String loginId = MapValueUtil.getString(backMap, "loginId");
                Integer organId = recipe.getClinicOrgan();
                DoctorDTO doctorDTO = doctorService.getByDoctorId(recipe.getDoctor());
                String userAccount = doctorDTO.getIdNumber();
                String caPassword = "";
                //签名时的密码从redis中获取
                if (null != redisClient.get("caPassword")) {
                    caPassword = redisClient.get("caPassword");
                }
                //标准化CA进行签名、签章==========================start=====

                //获取签章pdf数据。签名原文
                CaSealRequestTO requestSealTO = RecipeServiceEsignExt.signCreateRecipePDF(recipeId, true);
                //获取签章图片
                DoctorExtendService doctorExtendService = BasicAPI.getService(DoctorExtendService.class);
                DoctorExtendDTO doctorExtendDTO = doctorExtendService.getByDoctorId(recipe.getDoctor());
                if (doctorExtendDTO != null && doctorExtendDTO.getSealData() != null) {
                    requestSealTO.setSealBase64Str(doctorExtendDTO.getSealData());
                } else {
                    requestSealTO.setSealBase64Str("");
                }

//                CommonCAFactory caFactory = new CommonCAFactory();
                //通过工厂获取对应的实现CA类
                CAInterface caInterface = commonCAFactory.useCAFunction(organId);
                CaSignResultVo resultVo = caInterface.commonCASignAndSeal(requestSealTO, recipe, organId, userAccount, caPassword);
//                RecipeServiceEsignExt.saveSignRecipePDF(resultVo.getPdfBase64(), recipeId, loginId, resultVo.getSignCADate(), resultVo.getSignRecipeCode(), true);
                //date 20200618
                //修改标准ca成异步操作，原先逻辑不做任何处理，抽出单独的异步实现接口
                result.setCode(RecipeResultBean.NO_ADDRESS);
                return result;
//                String fileId = null;
//                result.setMsg(resultVo.getMsg());
//                //date20200617
//                //添加异步操作
//                if(-1 == resultVo.getCode()){
//                    result.setCode(RecipeResultBean.NO_ADDRESS);
//                    return result;
//                }
//                if (resultVo != null && 200 == resultVo.getCode()) {
//                    result.setCode(RecipeResultBean.SUCCESS);
//                    //保存签名值、时间戳、电子签章文件
//                    RecipeServiceEsignExt.saveSignRecipePDF(resultVo.getPdfBase64(), recipeId, loginId, resultVo.getSignCADate(), resultVo.getSignRecipeCode(), true, fileId);
//                    resultVo.setFileId(fileId);
//                    signRecipeInfoSave(recipeId, true, resultVo, organId);
//                    try {
//                        SignDoctorRecipeInfo signDoctorRecipeInfo = signRecipeInfoService.get(recipeId);
//                        JSONObject jsonObject = new JSONObject();
//                        jsonObject.put("recipeBean", JSONObject.toJSONString(recipe));
//                        jsonObject.put("details", JSONObject.toJSONString(details));
//                        signDoctorRecipeInfo.setSignBefText(jsonObject.toJSONString());
//                        signRecipeInfoService.update(signDoctorRecipeInfo);
//                    } catch (Exception e) {
//                        LOGGER.error("signBefText save error："  + e.getMessage(),e);
//                    }
//                }else{
//                    ISmsPushService smsPushService = AppContextHolder.getBean("eh.smsPushService", ISmsPushService.class);
//                    SmsInfoBean smsInfo = new SmsInfoBean();
//                    smsInfo.setBusId(0);
//                    smsInfo.setOrganId(0);
//                    smsInfo.setBusType("DocSignNotify");
//                    smsInfo.setSmsType("DocSignNotify");
//                    smsInfo.setExtendValue(doctorDTO.getUrt() + "|" + recipeId + "|" + doctorDTO.getLoginId());
//                    smsPushService.pushMsgData2OnsExtendValue(smsInfo);
//                    result.setCode(RecipeResultBean.FAIL);
//                }
//                else {
//                    RecipeLogDAO recipeLogDAO = DAOFactory.getDAO(RecipeLogDAO.class);
//                    RecipeLog recipeLog = new RecipeLog();
//                    recipeLog.setRecipeId(recipeId);
//                    recipeLog.setBeforeStatus(recipe.getStatus());
//                    recipeLog.setAfterStatus(RecipeStatusConstant.SIGN_ERROR_CODE_DOC);
//                    recipeLog.setMemo(resultVo.getMsg());
//                    recipeLog.setModifyDate(new Date());
//                    recipeLogDAO.saveRecipeLog(recipeLog);
//
//                    Map<String, Object> attrMap = Maps.newHashMap();
//                    attrMap.put("Status", RecipeStatusConstant.SIGN_ERROR_CODE_DOC);
//                    recipeDAO.updateRecipeInfoByRecipeId(recipeId,attrMap );
//
//                    ISmsPushService smsPushService = AppContextHolder.getBean("eh.smsPushService", ISmsPushService.class);
//                    SmsInfoBean smsInfo = new SmsInfoBean();
//                    smsInfo.setBusId(0);
//                    smsInfo.setOrganId(0);
//                    smsInfo.setBusType("SignNotify");
//                    smsInfo.setSmsType("SignNotify");
//                    smsInfo.setExtendValue(doctorDTO.getUrt() + "|" + recipeId + "|" + doctorDTO.getLoginId());
//                    smsPushService.pushMsgData2OnsExtendValue(smsInfo);
//                }

//                if (null != recipeFileId) {
//                    Map<String, Object> attrMap = Maps.newHashMap();
//                    attrMap.put("signFile", recipeFileId);
//                    attrMap.put("signDate", recipe.getSignDate());
//                    recipeDAO.updateRecipeInfoByRecipeId(recipeId, attrMap);
//                }
            } catch (Exception e) {
                LOGGER.error("generateRecipePdfAndSign 标准化CA签章报错 recipeId={} ,doctor={} ,e==============", recipeId, recipe.getDoctor(), e);
                result.setCode(RecipeResultBean.FAIL);
            }
            //标准化CA进行签名、签章==========================end=====
        } else {
            memo = "签名上传文件失败！原因：" + MapValueUtil.getString(backMap, "msg");
            LOGGER.error("generateRecipePdfAndSign 签名上传文件失败. recipeId={}, result={}", recipe.getRecipeId(), JSONUtils.toString(backMap));
        }


        //日志记录
        RecipeLogService.saveRecipeLog(recipeId, recipe.getStatus(), recipe.getStatus(), memo);
        return result;
    }

    private void doctorToRecipePDF(Integer recipeId) {
        Recipe recipe = recipeDAO.getByRecipeId(recipeId);
        doctorToRecipePDF(recipeId,recipe);
    }
    private void doctorToRecipePDF(Integer recipeId, Recipe recipe) {
        //在触发医生签名的时候将pdf先生成，回调的时候再将CA的返回更新
        //之所以不放置在CA回调里，是因为老流程里不是一定调用回调函数的
        try {
            //date 202001013 修改非易签保流程下的pdf
            Integer organId = recipe.getClinicOrgan();
            DoctorDTO doctorDTO = doctorService.getByDoctorId(recipe.getDoctor());
            String fileId ;
            boolean usePlatform = true;
            Object recipeUsePlatformCAPDF = configService.getConfiguration(organId, "recipeUsePlatformCAPDF");
            if(null != recipeUsePlatformCAPDF){
                usePlatform = Boolean.parseBoolean(recipeUsePlatformCAPDF.toString());
            }
            //保存签名值、时间戳、电子签章文件
            //使用平台CA模式，手动生成pdf
            //生成pdf分解成，先生成无医生药师签名的pdf，再将医生药师的签名放置在pdf上
            String pdfBase64Str;
            String signImageId;
            if(usePlatform){
                CaSealRequestTO requestSealTO = RecipeServiceEsignExt.signCreateRecipePDF(recipeId, true);
                if(null == requestSealTO){
                    LOGGER.warn("当前处方{}CA组装【pdf】和【签章数据】信息返回空, 产生CA模板pdf文件失败！", recipeId);
                }else{
                    //先将产生的pdf
                    //根据ca配置：签章显示是显示第三方的签章还是平台签章，默认使用平台签章
                    String sealDataFrom="platFormSeal";
                    try {
                        sealDataFrom = (String) configService.getConfiguration(recipe.getClinicOrgan(), "sealDataFrom");
                    }catch (Exception e){
                        LOGGER.error("doctorToRecipePDF 获取签章使用方配置error, recipeId:{}", recipeId, e);
                    }
                    signImageId = doctorDTO.getSignImage();
                    if("thirdSeal".equals(sealDataFrom)){
                        LOGGER.info("使用第三方签名，recipeId:{}",recipeId);
                        SignRecipeInfoService signRecipeInfoService = AppContextHolder.getBean("signRecipeInfoService", SignRecipeInfoService.class);
                        SignDoctorRecipeInfo docInfo = signRecipeInfoService.getSignInfoByRecipeIdAndServerType(recipeId, CARecipeTypeConstant.CA_RECIPE_DOC);
                        if(null != docInfo){
                            signImageId = docInfo.getSignPictureDoc();
                        }
                    }
                    pdfBase64Str = requestSealTO.getPdfBase64Str();
                    //将生成的处方pdf生成id
                    fileId = CreateRecipePdfUtil.generateDocSignImageInRecipePdf(recipeId, recipe.getDoctor(),
                            true, TCM_TEMPLATETYPE.equals(recipe.getRecipeType()), pdfBase64Str, signImageId);

                    //非使用平台CA模式的使用返回中的PdfBase64生成pdf文件
                    RecipeServiceEsignExt.saveSignRecipePDFCA(null, recipeId, null, null, null, true, fileId);
                }

            }
        } catch (Exception e) {
            LOGGER.warn("当前处方{}是使用平台医生部分pdf的,生成失败！", recipe.getRecipeId());
            //日志记录
            RecipeLogService.saveRecipeLog(recipeId, recipe.getStatus(), recipe.getStatus(), "平台医生部分pdf的生成失败");
        }
    }

    /**
     * 加@RpcService为了测试
     * 生成条形码在处方pdf文件上
     *
     * @param recipeId
     * @param recipeFileId
     * @param recipeCode
     */
    @RpcService
    public void generateBarCodeForRecipePdfAndSwap(Integer recipeId, String recipeFileId, String recipeCode) {
        if (StringUtils.isEmpty(recipeCode) || StringUtils.isEmpty(recipeFileId)) {
            return;
        }
        try {
            RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
            String newPfd = CreateRecipePdfUtil.generateBarCodeInRecipePdf(recipeFileId, recipeCode);
            if (StringUtils.isNotEmpty(newPfd)) {
                recipeDAO.updateRecipeInfoByRecipeId(recipeId, ImmutableMap.of("signFile", newPfd));
            }
        } catch (Exception e) {
            LOGGER.error("generateBarCodeForRecipePdfAndSwap error. recipeId={}", recipeId, e);
        }
    }

    //重试二次医生审核通过签名
    @Deprecated
    public void retryDoctorSecondCheckPass(Recipe recipe) {
        RecipeDAO recipeDAO = getDAO(RecipeDAO.class);

        Integer afterStatus = RecipeStatusConstant.CHECK_PASS_YS;
        //添加后置状态设置
        if (ReviewTypeConstant.Postposition_Check == recipe.getReviewType()) {
            if (!recipe.canMedicalPay()) {
                RecipeOrderDAO orderDAO = getDAO(RecipeOrderDAO.class);
                boolean effective = orderDAO.isEffectiveOrder(recipe.getOrderCode(), recipe.getPayMode());
                if (null != recipe.getOrderCode() && !effective) {
                    LOGGER.warn("当前处方{}已失效");
                    return;
                }
            } else {
                afterStatus = RecipeStatusConstant.CHECK_PASS;
            }
        } else if (ReviewTypeConstant.Preposition_Check == recipe.getReviewType()) {
            afterStatus = RecipeStatusConstant.CHECK_PASS;
        }
        if (!recipe.canMedicalPay()) {
            RecipeOrderDAO orderDAO = getDAO(RecipeOrderDAO.class);
            boolean effective = orderDAO.isEffectiveOrder(recipe.getOrderCode(), recipe.getPayMode());
            if (null != recipe.getOrderCode() && !effective) {
                LOGGER.warn("当前处方{}已失效");
                return;
            }
        } else {
            afterStatus = RecipeStatusConstant.CHECK_PASS;
        }
        Map<String, Object> updateMap = new HashMap<>();

        //date 20190929
        //这里提示文案描述，扩展成二次审核通过/二次审核不通过的说明
        recipeDAO.updateRecipeInfoByRecipeId(recipe.getRecipeId(), afterStatus, updateMap);
        afterCheckPassYs(recipe);
        //date20200227 判断前置的时候二次签名成功，发对应的消息
        if (ReviewTypeConstant.Preposition_Check == recipe.getReviewType()) {
            auditModeContext.getAuditModes(recipe.getReviewType()).afterCheckPassYs(recipe);
        }


    }

    //重试二次医生审核不通过签名
    @Deprecated
    public void retryDoctorSecondCheckNoPass(Recipe dbRecipe) {
        RecipeDAO recipeDAO = getDAO(RecipeDAO.class);
        //date 2020/1/2
        //发送二次不通过消息判断是否是二次审核不通过
        if (!RecipecCheckStatusConstant.Check_Normal.equals(dbRecipe.getCheckStatus())) {
            //添加发送不通过消息
            RecipeMsgService.batchSendMsg(dbRecipe, RecipeStatusConstant.CHECK_NOT_PASSYS_REACHPAY);
            //更新处方一次审核不通过标记
            Map<String, Object> updateMap = new HashMap<>();
            updateMap.put("checkStatus", RecipecCheckStatusConstant.Check_Normal);
            recipeDAO.updateRecipeInfoByRecipeId(dbRecipe.getRecipeId(), updateMap);
            //HIS消息发送
            //审核不通过 往his更新状态（已取消）
            Recipe recipe = recipeDAO.getByRecipeId(dbRecipe.getRecipeId());
            RecipeHisService hisService = ApplicationUtils.getRecipeService(RecipeHisService.class);
            hisService.recipeStatusUpdate(recipe.getRecipeId());
            //记录日志
            RecipeLogService.saveRecipeLog(recipe.getRecipeId(), recipe.getStatus(), recipe.getStatus(), "审核不通过处理完成");
        }

        //患者如果使用优惠券将优惠券解锁
        RecipeCouponService recipeCouponService = ApplicationUtils.getRecipeService(RecipeCouponService.class);
        recipeCouponService.unuseCouponByRecipeId(dbRecipe.getRecipeId());

        //根据审方模式改变--审核未通过处理
        auditModeContext.getAuditModes(dbRecipe.getReviewType()).afterCheckNotPassYs(dbRecipe);
    }

    //医生端二次审核签名重试
    @Deprecated
    @RpcService
    public void retryDoctorSecondSignCheck(Integer recipeId) {
        RecipeDAO recipeDAO = getDAO(RecipeDAO.class);
        RecipeLogDAO recipeLogDAO = getDAO(RecipeLogDAO.class);
        Recipe dbRecipe = recipeDAO.getByRecipeId(recipeId);
        //date 20200507
        //设置处方的状态为医生签名中
        if (null == dbRecipe) {
            LOGGER.warn("当前处方{}不存在!", recipeId);
            return;
        }
        recipeDAO.updateRecipeInfoByRecipeId(recipeId, ImmutableMap.of("status", RecipeStatusConstant.SIGN_ING_CODE_DOC));
        try {
            //写入his成功后，生成pdf并签名
            RecipeResultBean recipeSignResult = generateRecipePdfAndSign(dbRecipe.getRecipeId());
            //date 20200424
            //判断当前处方的状态为签名失败不走下面逻辑
//            if(new Integer(28).equals(getByRecipeId(dbRecipe.getRecipeId()).getStatus())){
//                return;
//            }
            if (RecipeResultBean.FAIL == recipeSignResult.getCode()) {
                //说明处方签名失败
                LOGGER.info("当前签名处方{}签名失败！", recipeId);
                recipeDAO.updateRecipeInfoByRecipeId(recipeId, RecipeStatusConstant.SIGN_ERROR_CODE_DOC, null);
                recipeLogDAO.saveRecipeLog(recipeId, dbRecipe.getStatus(), dbRecipe.getStatus(), recipeSignResult.getMsg());
                return;
            } else {
                //说明处方签名成功，记录日志，走签名成功逻辑
                LOGGER.info("当前签名处方{}签名成功！", recipeId);
                recipeLogDAO.saveRecipeLog(recipeId, dbRecipe.getStatus(), dbRecipe.getStatus(), "当前签名处方签名成功");
            }


        } catch (Exception e) {
            LOGGER.error("checkPassSuccess 签名服务或者发送卡片异常. ", e);
        }

        //根据处方单判断处方二次审核通过原因，判断是否通过
        //说明是二次审核不通过
        if (StringUtils.isEmpty(dbRecipe.getSupplementaryMemo())) {
            retryDoctorSecondCheckNoPass(dbRecipe);
        } else {
            //说明是二次审核通过
            retryDoctorSecondCheckPass(dbRecipe);
        }


    }

    //重试医生签名
    @RpcService
    public void retryDoctorSignCheck(Integer recipeId) {
        RecipeDAO recipeDAO = getDAO(RecipeDAO.class);
        RecipeLogDAO recipeLogDAO = getDAO(RecipeLogDAO.class);
        Recipe recipe = recipeDAO.getByRecipeId(recipeId);
        //date 20200507
        //设置处方的状态为医生签名中
        if (null == recipe) {
            LOGGER.warn("当前处方{}不存在!", recipeId);
            return;
        }

        String recipeMode = recipe.getRecipeMode();
        //重试签名，首先设置处方的状态为签名中，根据签名的结果
        // 设置处方的状态，如果失败不走下面逻辑

        Integer status = RecipeStatusConstant.CHECK_PASS;

        String memo = "HIS审核返回：写入his成功，审核通过";
        /*// 医保用户
        if (recipe.canMedicalPay()) {
            // 如果是中药或膏方处方不需要药师审核
            if (RecipeUtil.isTcmType(recipe.getRecipeType())) {
                status = RecipeStatusConstant.CHECK_PASS_YS;
                //memo = "HIS审核返回：写入his成功，药师审核通过";
                //date 医院审核日志记录放在前置机调用结果的时候记录
            }

        }*/

        //其他平台处方状态不变
        if (0 == recipe.getFromflag()) {
            status = recipe.getStatus();
            //memo = "HIS审核返回：写入his成功(其他平台处方)";
            //date 医院审核日志记录放在前置机调用结果的时候记录
        }

        try {
            //写入his成功后，生成pdf并签名
            //date 20200827 修改his返回请求CA
            Integer CANewOldWay = CA_OLD_TYPE;
            Object caProcessType = configService.getConfiguration(recipe.getClinicOrgan(), "CAProcessType");
            if(null != caProcessType){
                CANewOldWay = Integer.parseInt(caProcessType.toString());
            }
            RecipeResultBean recipeSignResult;
            if(CA_OLD_TYPE.equals(CANewOldWay)){
                recipeDAO.updateRecipeInfoByRecipeId(recipeId, ImmutableMap.of("status", RecipeStatusConstant.SIGN_ING_CODE_DOC));
                recipeSignResult = generateRecipePdfAndSign(recipe.getRecipeId());
            }else{
                //触发CA前置操作
                recipeSignResult = AbstractCaProcessType.getCaProcessFactory(recipe.getClinicOrgan()).hisCallBackCARecipeFunction(recipe.getRecipeId());
            }
            //date 20200617
            //添加逻辑：ca返回异步无结果
            if (RecipeResultBean.NO_ADDRESS.equals(recipeSignResult.getCode())) {
                return;
            }
            if (RecipeResultBean.FAIL.equals(recipeSignResult.getCode())) {
                //说明处方签名失败
                LOGGER.info("当前签名处方{}签名失败！", recipeId);
                recipeDAO.updateRecipeInfoByRecipeId(recipeId, RecipeStatusConstant.SIGN_ERROR_CODE_DOC, null);
                recipeLogDAO.saveRecipeLog(recipeId, recipe.getStatus(), recipe.getStatus(), recipeSignResult.getMsg());
                //CA同步回调的接口 发送环信消息
                if (new Integer(2).equals(recipe.getBussSource())) {
                    IRecipeOnLineRevisitService recipeOnLineRevisitService = RevisitAPI.getService(IRecipeOnLineRevisitService.class);
                    recipeOnLineRevisitService.sendRecipeDefeat(recipe.getRecipeId(), recipe.getClinicId());
                }
                return;
            } else {
                //说明处方签名成功，记录日志，走签名成功逻辑
                LOGGER.info("当前签名处方{}签名成功！", recipeId);
                //recipeLogDAO.saveRecipeLog(recipeId, recipe.getStatus(), recipe.getStatus(), "当前签名处方签名成功");
                //date 20200526
                if(CA_OLD_TYPE.equals(CANewOldWay)){
                    memo = "当前签名处方签名成功";
                }else{
                    memo = "当前签名处方签名成功---CA前置，his返回默认CA成功";
                }
            }
            RecipeMsgService.batchSendMsg(recipeId, RecipeMsgEnum.PRESCRIBE_SUCCESS.getStatus());
            //TODO 根据审方模式改变状态
            //设置处方签名成功后的处方的状态
            auditModeContext.getAuditModes(recipe.getReviewType()).afterHisCallBackChange(status, recipe, memo);

        } catch (Exception e) {
            LOGGER.error("checkPassSuccess 签名服务或者发送卡片异常. ", e);
        }

        if (RecipeBussConstant.RECIPEMODE_NGARIHEALTH.equals(recipeMode)) {
            //配送处方标记 1:只能配送 更改处方取药方式
            if (Integer.valueOf(1).equals(recipe.getDistributionFlag())) {
                try {
                    RecipeHisService hisService = ApplicationUtils.getRecipeService(RecipeHisService.class);
                    RecipeResultBean result1 = hisService.recipeDrugTake(recipe.getRecipeId(), PayConstant.PAY_FLAG_NOT_PAY, null);
                    if (RecipeResultBean.FAIL.equals(result1.getCode())) {
                        LOGGER.warn("retryDoctorSignCheck recipeId=[{}]更改取药方式失败，error=[{}]", recipe.getRecipeId(), result1.getError());
                        //不能影响流程去掉异常
                        /*throw new DAOException(ErrorCode.SERVICE_ERROR, "更改取药方式失败，错误:" + result1.getError());*/
                    }
                } catch (Exception e) {
                    LOGGER.warn("retryDoctorSignCheck recipeId=[{}]更改取药方式异常", recipe.getRecipeId(), e);
                }
            }
        }
        //2019/5/16 互联网模式--- 医生开完处方之后聊天界面系统消息提示
        if (RecipeBussConstant.RECIPEMODE_ZJJGPT.equals(recipeMode)) {
            /*//根据申请人mpiid，requestMode 获取当前咨询单consultId
            IConsultService iConsultService = ApplicationUtils.getConsultService(IConsultService.class);
            List<Integer> consultIds = iConsultService.findApplyingConsultByRequestMpiAndDoctorId(recipe.getRequestMpiId(),
                    recipe.getDoctor(), RecipeSystemConstant.CONSULT_TYPE_RECIPE);
            Integer consultId = null;
            if (CollectionUtils.isNotEmpty(consultIds)) {
                consultId = consultIds.get(0);
            }*/
            Integer consultId = recipe.getClinicId();
            if (null != consultId) {
                try {
                    if (RecipeBussConstant.BUSS_SOURCE_FZ.equals(recipe.getBussSource())) {
                        IRecipeOnLineRevisitService recipeOnLineConsultService = RevisitAPI.getService(IRecipeOnLineRevisitService.class);
                        recipeOnLineConsultService.sendRecipeMsg(consultId, 3);

                    } else if (RecipeBussConstant.BUSS_SOURCE_WZ.equals(recipe.getBussSource())) {
                        IRecipeOnLineConsultService recipeOnLineConsultService = ConsultAPI.getService(IRecipeOnLineConsultService.class);
                        recipeOnLineConsultService.sendRecipeMsg(consultId, 3);
                    }
                } catch (Exception e) {
                    LOGGER.error("retryDoctorSignCheck sendRecipeMsg error, type:3, consultId:{}, error:", consultId, e);
                }

            }
        }
        //推送处方到监管平台
        RecipeBusiThreadPool.submit(new PushRecipeToRegulationCallable(recipe.getRecipeId(), 1));

        //将原先互联网回调修改处方的推送的逻辑移到这里
        //判断是否是阿里药企，是阿里大药房就推送处方给药企
        OrganAndDrugsepRelationDAO organAndDrugsepRelationDAO = DAOFactory.getDAO(OrganAndDrugsepRelationDAO.class);
        List<DrugsEnterprise> drugsEnterprises = organAndDrugsepRelationDAO.findDrugsEnterpriseByOrganIdAndStatus(recipe.getClinicOrgan(), 1);
        if (CollectionUtils.isEmpty(drugsEnterprises)) {
            return;
        }
        DrugsEnterprise drugsEnterprise = drugsEnterprises.get(0);
        if ("aldyf".equals(drugsEnterprise.getCallSys())) {
            //判断用户是否已鉴权
            if (StringUtils.isNotEmpty(recipe.getRequestMpiId())) {
                DrugDistributionService drugDistributionService = ApplicationUtils.getRecipeService(DrugDistributionService.class);
                PatientService patientService = BasicAPI.getService(PatientService.class);
                String loginId = patientService.getLoginIdByMpiId(recipe.getRequestMpiId());
                if (drugDistributionService.authorization(loginId)) {
                    //推送阿里处方推片和信息
                    if (null == drugsEnterprise) {
                        LOGGER.warn("updateRecipeStatus aldyf 药企不存在");
                    }
                    RemoteDrugEnterpriseService remoteDrugEnterpriseService = ApplicationUtils.getRecipeService(RemoteDrugEnterpriseService.class);
                    DrugEnterpriseResult deptResult =
                            remoteDrugEnterpriseService.pushSingleRecipeInfoWithDepId(recipeId, drugsEnterprise.getId());
                    LOGGER.info("updateRecipeStatus 推送药企处方，result={}", JSONUtils.toString(deptResult));
                }
            }
        }

    }

    //date 20200610
    //上海胸科ca通过回调的方式回写医生ca结果给平台触发业务流程
    //date 20201013 统一修改返回
    //pdf的生成统一逻辑在回调函数里：
    //首先易签保CA的pdf按原流程生成，非易签保的统一按照取pdf的配置来设置处方pdf
    //当取平台pdf时候，使用易签保的pdf，签名按照本地的签名来（深圳CA特别处理，用CA的签名图片）；
    //当取CApdf时候，直接回去CA请求返回的ca数据保存
    @RpcService
    public void retryCaDoctorCallBackToRecipe(CaSignResultVo resultVo) {
        //ca完成签名签章后，将和返回的结果给平台
        //平台根据结果设置处方业务的跳转
        if (null == resultVo) {
            LOGGER.warn("当期医生ca签名异步调用接口返回参数为空，无法设置相关信息");
            return;
        }
        LOGGER.info("当前ca异步接口返回：{}", JSONUtils.toString(resultVo));
        RecipeDAO recipeDAO = getDAO(RecipeDAO.class);
        RecipeDetailDAO recipeDetailDAO = getDAO(RecipeDetailDAO.class);
        RecipeLogDAO recipeLogDAO = getDAO(RecipeLogDAO.class);
        Integer recipeId = resultVo.getRecipeId();

        Recipe recipe = recipeDAO.getByRecipeId(recipeId);
        List<Recipedetail> details = recipeDetailDAO.findByRecipeId(recipeId);
        RecipeResultBean result = RecipeResultBean.getFail();

        Integer organId = recipe.getClinicOrgan();
        DoctorDTO doctorDTO = doctorService.getByDoctorId(recipe.getDoctor());

        Map<String, Object> esignResponseMap = resultVo.getEsignResponseMap();
        Integer CANewOldWay = CA_OLD_TYPE;
        Object caProcessType = configService.getConfiguration(organId, "CAProcessType");
        if(null != caProcessType){
            CANewOldWay = Integer.parseInt(caProcessType.toString());
        }
        try {
            String fileId = null;
            result.setMsg(resultVo.getMsg());
            //添加兼容医生CA易签保的回调逻辑
            if(MapUtils.isNotEmpty(esignResponseMap)){
                String imgFileId = MapValueUtil.getString(esignResponseMap, "imgFileId");
                Map<String, Object> attrMapimg = Maps.newHashMap();
                attrMapimg.put("signImg", imgFileId);
                recipeDAO.updateRecipeInfoByRecipeId(recipeId, attrMapimg);
                LOGGER.info("generateRecipeImg 签名图片成功. fileId={}, recipeId={}", imgFileId, recipe.getRecipeId());
                //易签保返回0表示成功
                Integer code = MapValueUtil.getInteger(esignResponseMap, "code");
                if(new Integer(0).equals(code)){
                    result.setCode(RecipeResultBean.SUCCESS);
                }
            }else{
                if (resultVo != null && new Integer(200).equals(resultVo.getCode())) {
                    result.setCode(RecipeResultBean.SUCCESS);

                    //date 202001013 修改非易签保流程下的pdf
                    boolean usePlatform = true;
                    Object recipeUsePlatformCAPDF = configService.getConfiguration(organId, "recipeUsePlatformCAPDF");
                    if(null != recipeUsePlatformCAPDF){
                        usePlatform = Boolean.parseBoolean(recipeUsePlatformCAPDF.toString());
                    }
                    //保存签名值、时间戳、电子签章文件
                    String pdfString = null;
                    if(!usePlatform){
                        if(null == resultVo.getPdfBase64()){
                            LOGGER.warn("当前处方{}使用CApdf返回CA图片为空！", recipeId);
                        }
                        pdfString = resultVo.getPdfBase64();
                    }else{
                        //需要调整逻辑：
                        //老流程上一层已经统一走了pdf优化生成，新流程统一在当前回调函数里进行
                        if(CA_NEW_TYPE.equals(CANewOldWay)){
                            doctorToRecipePDF(recipeId, recipe);
                        }
                    }
                    //非使用平台CA模式的使用返回中的PdfBase64生成pdf文件
                    RecipeServiceEsignExt.saveSignRecipePDFCA(pdfString, recipeId, null, resultVo.getSignCADate(), resultVo.getSignRecipeCode(), true, fileId);
                    resultVo.setFileId(fileId);
                    //date 20200922
                    //老流程保存sign，新流程已经移动至CA保存
                    if(CA_OLD_TYPE.equals(CANewOldWay)){
                        signRecipeInfoSave(recipeId, true, resultVo, organId);
                        try {
                            SignDoctorRecipeInfo signDoctorRecipeInfo = signRecipeInfoService.get(recipeId);
                            JSONObject jsonObject = new JSONObject();
                            jsonObject.put("recipeBean", JSONObject.toJSONString(recipe));
                            jsonObject.put("details", JSONObject.toJSONString(details));
                            signDoctorRecipeInfo.setSignBefText(jsonObject.toJSONString());
                            signRecipeInfoService.update(signDoctorRecipeInfo);
                        } catch (Exception e) {
                            LOGGER.error("signBefText save error：" + e.getMessage(), e);
                        }
                    }
                } else {
                    ISmsPushService smsPushService = AppContextHolder.getBean("eh.smsPushService", ISmsPushService.class);
                    SmsInfoBean smsInfo = new SmsInfoBean();
                    smsInfo.setBusId(0);
                    smsInfo.setOrganId(0);
                    smsInfo.setBusType("DocSignNotify");
                    smsInfo.setSmsType("DocSignNotify");
                    smsInfo.setExtendValue(doctorDTO.getUrt() + "|" + recipeId + "|" + doctorDTO.getLoginId());
                    smsPushService.pushMsgData2OnsExtendValue(smsInfo);
                    result.setCode(RecipeResultBean.FAIL);
                }
            }
        } catch (Exception e) {
            LOGGER.error("generateRecipePdfAndSign 标准化CA签章报错 recipeId={} ,doctor={} ,e==============", recipeId, recipe.getDoctor(), e);
        }

        //首先判断当前ca是否是有结束结果的
        if (-1 == resultVo.getResultCode()) {
            LOGGER.info("当期处方{}医生ca签名异步调用接口返回：未触发处方业务结果", recipeId);
            return;
        }

        //重试签名，首先设置处方的状态为签名中，根据签名的结果
        // 设置处方的状态，如果失败不走下面逻辑
        Integer code = result.getCode();
        String msg = result.getMsg();
        Integer status = RecipeStatusConstant.CHECK_PASS;

        String memo = "HIS审核返回：写入his成功，审核通过";
        /*// 医保用户
        if (recipe.canMedicalPay()) {
            // 如果是中药或膏方处方不需要药师审核
            if (RecipeUtil.isTcmType(recipe.getRecipeType())) {
                status = RecipeStatusConstant.CHECK_PASS_YS;
                memo = "HIS审核返回：写入his成功，药师审核通过";
            }

        }*/

        //其他平台处方状态不变
        if (0 == recipe.getFromflag()) {
            status = recipe.getStatus();
            memo = "HIS审核返回：写入his成功(其他平台处方)";
        }
        try {
            if (RecipeResultBean.FAIL == code) {
                //说明处方签名失败
                LOGGER.info("当前签名处方{}签名失败！", recipeId);
                recipeDAO.updateRecipeInfoByRecipeId(recipeId, RecipeStatusConstant.SIGN_ERROR_CODE_DOC, null);
                recipeLogDAO.saveRecipeLog(recipeId, recipe.getStatus(), recipe.getStatus(), msg);
                //CA异步回调的接口 发送环信消息
                if(new Integer(2).equals(recipe.getBussSource())){
                    IRecipeOnLineRevisitService recipeOnLineRevisitService = RevisitAPI.getService(IRecipeOnLineRevisitService.class);
                    recipeOnLineRevisitService.sendRecipeDefeat(recipe.getRecipeId(),recipe.getClinicId());
                }
                return;
            } else {
                //说明处方签名成功，记录日志，走签名成功逻辑
                LOGGER.info("当前签名处方{}签名成功！", recipeId);
                recipeLogDAO.saveRecipeLog(recipeId, recipe.getStatus(), recipe.getStatus(), "当前签名处方签名成功");
                //添加兼容医生CA易签保的回调逻辑
                if(MapUtils.isNotEmpty(esignResponseMap)){
                    String recipeFileId = MapValueUtil.getString(esignResponseMap, "fileId");
                    Map<String, Object> attrMap = Maps.newHashMap();
                    attrMap.put("signFile", recipeFileId);
                    attrMap.put("signDate", recipe.getSignDate());
                    recipeDAO.updateRecipeInfoByRecipeId(recipeId, attrMap);
                    memo = "签名上传文件成功, fileId=" + recipeFileId;
                    LOGGER.info("generateRecipePdfAndSign 签名成功. fileId={}, recipeId={}", recipeFileId, recipe.getRecipeId());
                    //个性化医院处方笺生成条形码
                    Set<String> organIdList = redisClient.sMembers(CacheConstant.KEY_BARCODEFORRECIPE_ORGAN_LIST);
                    if (CollectionUtils.isNotEmpty(organIdList) && organIdList.contains(String.valueOf(recipe.getClinicOrgan()))) {
                        RecipeBusiThreadPool.execute(() -> generateBarCodeForRecipePdfAndSwap(recipeId, recipeFileId, recipe.getRecipeCode()));
                    }
                }
            }

        } catch (Exception e) {
            LOGGER.error("checkPassSuccess 签名服务或者发送卡片异常. ", e);
        }

        //设置处方的状态，如果失败不走下面逻辑
        /**************/
        //触发CA操作
        //兼容新老版本,根据配置项判断CA的新老流程走向
        RecipeBean recipeBean = getByRecipeId(recipeId);
        List<RecipeDetailBean> detailBeanList = ObjectCopyUtils.convert(details, RecipeDetailBean.class);
        if(CA_NEW_TYPE.equals(CANewOldWay)){
            AbstractCaProcessType.getCaProcessFactory(recipeBean.getClinicOrgan()).signCAAfterRecipeCallBackFunction(recipeBean, detailBeanList);
        }else{
            //老版默认走后置的逻辑，直接将处方向下流
            caAfterProcessType.signCAAfterRecipeCallBackFunction(recipeBean, detailBeanList);
        }
    }

    //date 20200610
    //上海胸科ca通过回调的方式回写ca药师结果给平台触发业务流程
    @RpcService
    public void retryCaPharmacistCallBackToRecipe(CaSignResultVo resultVo) {
        //ca完成签名签章后，将和返回的结果给平台
        //平台根据结果设置处方业务的跳转
        if (null == resultVo) {
            LOGGER.warn("当期药师签名异步调用接口返回参数为空，无法设置相关信息");
            return;
        }
        LOGGER.info("当前ca异步接口返回：{}", JSONUtils.toString(resultVo));
        RecipeDAO recipeDAO = getDAO(RecipeDAO.class);
        RecipeDetailDAO recipeDetailDAO = getDAO(RecipeDetailDAO.class);
        RecipeLogDAO recipeLogDAO = getDAO(RecipeLogDAO.class);
        Integer recipeId = resultVo.getRecipeId();

        Recipe recipe = recipeDAO.getByRecipeId(recipeId);
        RecipeExtend recipeExtend = recipeExtendDAO.getByRecipeId(recipe.getRecipeId());
        EmrRecipeManager.getMedicalInfo(recipe, recipeExtend);

        Integer organId = recipe.getClinicOrgan();
        RecipeResultBean checkResult = RecipeResultBean.getFail();

        Map<String, Object> esignResponseMap = resultVo.getEsignResponseMap();
        Integer CANewOldWay = CA_OLD_TYPE;
        Object caProcessType = configService.getConfiguration(organId, "CAProcessType");
        if(null != caProcessType){
            CANewOldWay = Integer.parseInt(caProcessType.toString());
        }
        try {
            String fileId = null;
            DoctorDTO doctorDTOn = doctorService.getByDoctorId(recipe.getChecker());
            if (null == doctorDTOn) {
                LOGGER.warn("当前处方{}审核药师为空，请检查处方相关信息", recipeId);
                return;
            }
            if(MapUtils.isNotEmpty(esignResponseMap)){
                LOGGER.info("reviewRecipe  esignService backMap:{} ,e=============", JSONUtils.toString(esignResponseMap));
                //易签保返回0表示成功
                Integer code = MapValueUtil.getInteger(esignResponseMap, "code");
                if(new Integer(0).equals(code)){
                    checkResult.setCode(RecipeResultBean.SUCCESS);
                }
            }else{

                if (resultVo != null && new Integer(200).equals(resultVo.getCode())) {

                    //date 202001013 修改非易签保流程下的pdf
                    boolean usePlatform = true;
                    Object recipeUsePlatformCAPDF = configService.getConfiguration(organId, "recipeUsePlatformCAPDF");
                    if(null != recipeUsePlatformCAPDF){
                        usePlatform = Boolean.parseBoolean(recipeUsePlatformCAPDF.toString());
                    }
                    //使用平台CA模式，手动生成pdf
                    //生成pdf分解成，先生成无医生药师签名的pdf，再将医生药师的签名放置在pdf上
                    String pdfString = null;
                    if(!usePlatform) {
                        if(null == resultVo.getPdfBase64()){
                            LOGGER.warn("当前处方[}返回CA图片为空！", recipeId);
                        }
                        //只有当使用CApdf的时候才去赋值
                        pdfString = resultVo.getPdfBase64();
                    }else{
                        //需要调整逻辑：
                        //老流程上一层已经统一走了pdf优化生成，新流程统一在当前回调函数里进行
                        if(CA_NEW_TYPE.equals(CANewOldWay)){
                            pharmacyToRecipePDF(recipeId);
                        }
                    }
                    //保存签名值、时间戳、电子签章文件
                    checkResult.setCode(RecipeResultBean.SUCCESS);
                    RecipeServiceEsignExt.saveSignRecipePDFCA(pdfString, recipeId, null, resultVo.getSignCADate(), resultVo.getSignRecipeCode(), false, fileId);
                    resultVo.setFileId(fileId);
                    //date 20200922
                    //老流程保存sign，新流程已经移动至CA保存
                    if(CA_OLD_TYPE.equals(CANewOldWay)){
                        signRecipeInfoSave(recipeId, false, resultVo, organId);
                    }
                } else {
                    ISmsPushService smsPushService = AppContextHolder.getBean("eh.smsPushService", ISmsPushService.class);
                    SmsInfoBean smsInfo = new SmsInfoBean();
                    smsInfo.setBusId(0);
                    smsInfo.setOrganId(0);
                    smsInfo.setBusType("PhaSignNotify");
                    smsInfo.setSmsType("PhaSignNotify");
                    smsInfo.setExtendValue(doctorDTOn.getUrt() + "|" + recipeId + "|" + doctorDTOn.getLoginId());
                    smsPushService.pushMsgData2OnsExtendValue(smsInfo);
                    checkResult.setCode(RecipeResultBean.FAIL);
                }
            }

        } catch (Exception e) {
            LOGGER.error("reviewRecipe  signFile 标准化CA签章报错 recipeId={} ,doctor={} ,e=============", recipeId, recipe.getDoctor(), e);
        }

        //首先判断当前ca是否是有结束结果的
        if (-1 == resultVo.getResultCode()) {
            LOGGER.info("当期处方{}药师ca签名异步调用接口返回：未触发处方业务结果", recipeId);
            return;
        }

        if (RecipeResultBean.FAIL == checkResult.getCode()) {
            //说明处方签名失败
            LOGGER.info("当前审核处方{}签名失败！", recipeId);
            recipeDAO.updateRecipeInfoByRecipeId(recipeId, RecipeStatusConstant.SIGN_ERROR_CODE_PHA, null);
            recipeLogDAO.saveRecipeLog(recipeId, recipe.getStatus(), recipe.getStatus(), checkResult.getMsg());
            return;
        } else {
            //说明处方签名成功，记录日志，走签名成功逻辑
            LOGGER.info("当前审核处方{}签名成功！", recipeId);
            recipeLogDAO.saveRecipeLog(recipeId, recipe.getStatus(), recipe.getStatus(), "当前审核处方签名成功");
            if(MapUtils.isNotEmpty(esignResponseMap)){
                String recipeFileId = MapValueUtil.getString(esignResponseMap, "fileId");
                boolean updateCheckPdf = recipeDAO.updateRecipeInfoByRecipeId(recipeId, ImmutableMap.<String, Object>of("chemistSignFile", recipeFileId));
                LOGGER.info("当前处方更新药师签名pdf结果：{}", updateCheckPdf);
            }
        }
        //组装审核的结果重新判断审核通过审核不通过
        //根据当前处方最新的审核结果判断审核，获取审核的结果
        CheckYsInfoBean resultBean = new CheckYsInfoBean();
        IRecipeCheckService recipeCheckService=  RecipeAuditAPI.getService(IRecipeCheckService.class,"recipeCheckServiceImpl");
        IRecipeCheckDetailService recipeCheckDetailService=  RecipeAuditAPI.getService(IRecipeCheckDetailService.class,"recipeCheckDetailServiceImpl");
        RecipeCheckBean recipeCheckBean = recipeCheckService.getNowCheckResultByRecipeId(recipe.getRecipeId());
        if (null == recipeCheckBean) {
            LOGGER.warn("当前药师签名的处方{}没有审核结果，无法进行签名", recipeId);
            return;
        }
        resultBean.setCheckFailMemo(recipe.getCheckFailMemo());
        resultBean.setCheckResult(recipeCheckBean.getCheckStatus());
        List<RecipeCheckDetailBean> recipeCheckDetailBeans = recipeCheckDetailService.findByCheckId(recipeCheckBean.getCheckId());
        List<RecipeCheckDetail> recipeCheckDetails=  ObjectCopyUtils.convert(recipeCheckDetailBeans,RecipeCheckDetail.class);
        resultBean.setCheckDetailList(recipeCheckDetails);
        int resultNow = recipeCheckBean.getCheckStatus();

        //date 20200512
        //更新处方审核结果状态
        int recipeStatus = RecipeStatusConstant.CHECK_NOT_PASS_YS;
        if (1 == resultNow) {
            //根据审方模式改变状态
            recipeStatus = auditModeContext.getAuditModes(recipe.getReviewType()).afterAuditRecipeChange();
            if (recipe.canMedicalPay()) {
                //如果是可医保支付的单子，审核是在用户看到之前，所以审核通过之后变为待处理状态
                recipeStatus = RecipeStatusConstant.CHECK_PASS;
            }
        }
        recipeDAO.updateRecipeInfoByRecipeId(recipeId, recipeStatus, null);
        //审核成功往药厂发消息
        //审方做异步处理
        GlobalEventExecFactory.instance().getExecutor().submit(new Runnable() {
            @Override
            public void run() {
                if (1 == resultNow) {
                    //审方成功，订单状态的
                    auditModeContext.getAuditModes(recipe.getReviewType()).afterCheckPassYs(recipe);
                } else {
                    //审核不通过后处理
                    doAfterCheckNotPassYs(recipe);
                }
                //将审核结果推送HIS
                try {
                    RecipeHisService hisService = ApplicationUtils.getRecipeService(RecipeHisService.class);
                    hisService.recipeAudit(recipe, resultBean);
                } catch (Exception e) {
                    LOGGER.warn("saveCheckResult send recipeAudit to his error. recipeId={}", recipeId, e);
                }
                if (RecipeBussConstant.RECIPEMODE_NGARIHEALTH.equals(recipe.getRecipeMode())) {
                    //增加药师首页待处理任务---完成任务
                    ApplicationUtils.getBaseService(IAsynDoBussService.class).fireEvent(new BussFinishEvent(recipeId, BussTypeConstant.RECIPE));
                }
            }
        });
        //推送处方到监管平台(审核后数据)
        RecipeBusiThreadPool.submit(new PushRecipeToRegulationCallable(recipe.getRecipeId(), 2));

    }


    //暂时只有西药能生成处方图片
    public void generateRecipeImageAndUpload(Integer recipeId, Map<String, Object> paramMap) {
        String fileName = "img_recipe_" + recipeId + ".jpg";
        try {
            /*//先生成本地图片文件----这里通过esign去生成
            byte[] bytes = CreateRecipeImageUtil.createImg(paramMap);*/
            paramMap.put("recipeImgId", recipeId);
            /*paramMap.put("recipeImgData",bytes);*/
        } catch (Exception e) {
            LOGGER.error("uploadRecipeImgSignFile exception:" + e.getMessage(), e);
        }
    }



    /**
     * 重试
     *
     * @param recipeId
     */
    @RpcService
    public RecipeResultBean sendNewRecipeToHIS(Integer recipeId) {
        RecipeResultBean resultBean = RecipeResultBean.getSuccess();
        RecipeDAO recipeDAO = getDAO(RecipeDAO.class);
        RecipeHisService hisService = ApplicationUtils.getRecipeService(RecipeHisService.class);

        Integer status = recipeDAO.getStatusByRecipeId(recipeId);
        //date 20191127
        //重试功能添加his写入失败的处方
        if (null == status || (status != RecipeStatusConstant.CHECKING_HOS && status != RecipeStatusConstant.HIS_FAIL)) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "该处方不能重试");
        }

        //HIS消息发送
        RecipeResultBean scanResult = hisService.scanDrugStockByRecipeId(recipeId);
        if (RecipeResultBean.FAIL.equals(scanResult.getCode())) {
            resultBean.setCode(scanResult.getCode());
            resultBean.setMsg(scanResult.getError());
            if (EXTEND_VALUE_FLAG.equals(scanResult.getExtendValue())) {
                resultBean.setError(scanResult.getError());
            }
            return resultBean;
        }

        hisService.recipeSendHis(recipeId, null);
        return resultBean;
    }

    /**
     * 发送只能配送处方，当医院库存不足时医生略过库存提醒后调用
     *
     * @param recipeBean
     * @return
     */
    @RpcService
    public Map<String, Object> sendDistributionRecipe(RecipeBean recipeBean, List<RecipeDetailBean> detailBeanList) {
        if (null == recipeBean) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "传入参数为空");
        }

        recipeBean.setDistributionFlag(1);
        recipeBean.setGiveMode(RecipeBussConstant.GIVEMODE_SEND_TO_HOME);
        return doSignRecipeExt(recipeBean, detailBeanList);
    }

    /**
     * 签名服务（新）
     *
     * @param recipeBean  处方
     * @param detailBeanList 详情
     * @param continueFlag 校验标识
     * @return Map<String ,   Object>
     */
    @RpcService
    public Map<String, Object> doSignRecipeNew(RecipeBean recipeBean, List<RecipeDetailBean> detailBeanList, int continueFlag) {
        LOGGER.info("RecipeService.doSignRecipeNew param: recipeBean={} detailBean={}", JSONUtils.toString(recipeBean), JSONUtils.toString(detailBeanList));
        //将密码放到redis中
        redisClient.set("caPassword", recipeBean.getCaPassword());
        Map<String, Object> rMap = new HashMap<String, Object>();
        rMap.put("signResult", true);
        try {
            recipeBean.setDistributionFlag(continueFlag);
            //上海肺科个性化处理--智能审方重要警示弹窗处理
            doforShangHaiFeiKe(recipeBean, detailBeanList);
            //第一步暂存处方（处方状态未签名）
            doSignRecipeSave(recipeBean, detailBeanList);

            //第二步预校验
            if(continueFlag == 0){
                //his处方预检查
                RecipeSignService recipeSignService = AppContextHolder.getBean("eh.recipeSignService", RecipeSignService.class);
                boolean b = recipeSignService.hisRecipeCheck(rMap, recipeBean);
                if (!b){
                    rMap.put("signResult", false);
                    rMap.put("recipeId", recipeBean.getRecipeId());
                    rMap.put("errorFlag", true);
                    return rMap;
                }
            }
            //第三步校验库存
            if(continueFlag == 0 || continueFlag == 4){
                rMap = doSignRecipeCheck(recipeBean);
                Boolean signResult = Boolean.valueOf(rMap.get("signResult").toString());
                if(signResult != null && false == signResult){
                    return rMap;
                }
            }
            //跳转所需要的复诊信息
            Integer consultId = recipeBean.getClinicId();
            Integer bussSource = recipeBean.getBussSource();
            if (consultId != null) {
                if (null != rMap && null == rMap.get("consultId")) {
                    rMap.put("consultId", consultId);
                    rMap.put("bussSource", bussSource);
                }
            }
            //date 2020-11-04将CA的触发放置在开处方最后
            PrescriptionService prescriptionService = ApplicationUtils.getRecipeService(PrescriptionService.class);
            if (prescriptionService.getIntellectJudicialFlag(recipeBean.getClinicOrgan()) == 1) {
                //更新审方信息
                RecipeBusiThreadPool.execute(new SaveAutoReviewRunable(recipeBean, detailBeanList));
            }
            //健康卡数据上传
            RecipeBusiThreadPool.execute(new CardDataUploadRunable(recipeBean.getClinicOrgan(), recipeBean.getMpiid(),"010106"));

            Integer CANewOldWay = CA_OLD_TYPE;
            Object caProcessType = configService.getConfiguration(recipeBean.getClinicOrgan(), "CAProcessType");
            if(null != caProcessType){
                CANewOldWay = Integer.parseInt(caProcessType.toString());
            }
            //触发CA前置操作
            if(CA_NEW_TYPE.equals(CANewOldWay)){
                AbstractCaProcessType.getCaProcessFactory(recipeBean.getClinicOrgan()).signCABeforeRecipeFunction(recipeBean, detailBeanList);
            }else{
                //老版默认走后置的逻辑，直接将处方推his
                caAfterProcessType.signCABeforeRecipeFunction(recipeBean, detailBeanList);
            }

        } catch (Exception e) {
            LOGGER.error("doSignRecipeNew error", e);
            throw new DAOException(recipe.constant.ErrorCode.SERVICE_ERROR, e.getMessage());
        }

        rMap.put("signResult", true);
        rMap.put("recipeId", recipeBean.getRecipeId());
        rMap.put("consultId", recipeBean.getClinicId());
        rMap.put("errorFlag", false);
        rMap.put("canContinueFlag", "0");
        LOGGER.info("doSignRecipeNew execute ok! rMap:" + JSONUtils.toString(rMap));
        return rMap;
    }


    /**
     * 签名服务（处方存储）
     *
     * @param recipe  处方
     * @param details 详情
     * @return
     */
    @RpcService
    public void doSignRecipeSave(RecipeBean recipe, List<RecipeDetailBean> details) {
        RecipeDAO recipeDAO = getDAO(RecipeDAO.class);
        PatientDTO patient = patientService.get(recipe.getMpiid());
        //解决旧版本因为wx2.6患者身份证为null，而业务申请不成功
        if (patient == null || StringUtils.isEmpty(patient.getCertificate())) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "该患者还未填写身份证信息，不能开处方");
        }
        // 就诊人改造：为了确保删除就诊人后历史处方不会丢失，加入主账号用户id
        //bug#46436 本人就诊人被删除保存不了导致后续微信模板消息重复推送多次
        List<PatientDTO> requestPatients = patientService.findOwnPatient(patient.getLoginId());
        if (CollectionUtils.isNotEmpty(requestPatients)){
            PatientDTO requestPatient = requestPatients.get(0);
            if (null != requestPatient && null != requestPatient.getMpiId()) {
                recipe.setRequestMpiId(requestPatient.getMpiId());
                // urt用于系统消息推送
                recipe.setRequestUrt(requestPatient.getUrt());
            }
        }
        //如果前端没有传入咨询id则从进行中的复诊或者咨询里取
        //获取咨询单id,有进行中的复诊则优先取复诊，若没有则取进行中的图文咨询
        if (recipe.getClinicId() == null) {
            getConsultIdForRecipeSource(recipe);
        }
        recipe.setStatus(RecipeStatusConstant.UNSIGN);
        recipe.setSignDate(DateTime.now().toDate());
        Integer recipeId = recipe.getRecipeId();
        //如果是已经暂存过的处方单，要去数据库取状态 判断能不能进行签名操作
        if (null != recipeId && recipeId > 0) {
            Integer status = recipeDAO.getStatusByRecipeId(recipeId);
            if (null == status || status > RecipeStatusConstant.UNSIGN) {
                throw new DAOException(ErrorCode.SERVICE_ERROR, "处方单已处理,不能重复签名");
            }

            updateRecipeAndDetail(recipe, details);
        } else {
            recipeId = saveRecipeData(recipe, details);
            recipe.setRecipeId(recipeId);
        }
    }

    /**
     * 处方签名校验服务
     *
     * @param recipe  处方
     * @return Map<String, Object>
     */
    @RpcService
    public Map<String, Object> doSignRecipeCheck(RecipeBean recipe) {
        RecipeHisService hisService = ApplicationUtils.getRecipeService(RecipeHisService.class);
        DrugsEnterpriseService drugsEnterpriseService = ApplicationUtils.getRecipeService(DrugsEnterpriseService.class);

        Map<String, Object> rMap = Maps.newHashMap();
        Integer recipeId = recipe.getRecipeId();
        //获取配置项
        IConfigurationCenterUtilsService configService = BaseAPI.getService(IConfigurationCenterUtilsService.class);
        //添加按钮配置项key
        Object payModeDeploy = configService.getConfiguration(recipe.getClinicOrgan(), "payModeDeploy");

        int checkFlag = 0;
        if(null != payModeDeploy){
            List<String> configurations = new ArrayList<>(Arrays.asList((String[])payModeDeploy));
            //收集按钮信息用于判断校验哪边库存 0是什么都没有，1是指配置了到院取药，2是配置到药企相关，3是医院药企都配置了
            if(configurations == null || configurations.size() == 0){
                rMap.put("signResult", false);
                rMap.put("errorFlag", true);
                rMap.put("msg", "抱歉，机构未配置购药方式，无法开处方。");
                rMap.put("canContinueFlag", "-1");
                LOGGER.info("doSignRecipeCheck recipeId={},msg={}",recipeId,rMap.get("msg"));
                return rMap;
            }
            for (String configuration : configurations) {
                switch (configuration){
                    case "supportToHos":
                        if(checkFlag == 0 || checkFlag == 1){
                            checkFlag = 1;
                        }else{
                            checkFlag = 3;
                        }
                        break;
                    case "supportOnline":
                    case "supportTFDS":
                        if(checkFlag == 0 || checkFlag == 2){
                            checkFlag = 2;
                        }else{
                            checkFlag = 3;
                        }
                        break;
                }

            }
        } else {
            rMap.put("signResult", false);
            rMap.put("errorFlag", true);
            rMap.put("msg", "抱歉，机构未配置购药方式，无法开处方。");
            rMap.put("canContinueFlag", "-1");
            LOGGER.info("doSignRecipeCheck recipeId={},msg={}",recipeId,rMap.get("msg"));
            return rMap;
        }

        rMap.put("recipeId", recipeId);
        switch (checkFlag){
            case 1:
                //只校验医院库存医院库存不校验药企，如无库存不允许开，直接弹出提示
                //HIS消息发送
                RecipeResultBean scanResult = hisService.scanDrugStockByRecipeId(recipeId);
                if (RecipeResultBean.FAIL.equals(scanResult.getCode())) {
                    rMap.put("signResult", false);
                    rMap.put("errorFlag", true);
                    List<String> nameList = (List<String>) scanResult.getObject();
                    rMap.put("msg", "【库存不足】由于" + Joiner.on(",").join(nameList) + "门诊药房库存不足，请更换其他药品后再试。");
                    rMap.put("canContinueFlag", "-1");
                    LOGGER.info("doSignRecipeCheck recipeId={},msg={}",recipeId,rMap.get("msg"));
                    return rMap;
                }
                break;
            case 2:
                //只校验处方药品药企配送以及库存信息，不校验医院库存
                boolean checkEnterprise = drugsEnterpriseService.checkEnterprise(recipe.getClinicOrgan());
                if (checkEnterprise) {
                    //验证能否药品配送以及能否开具到一张处方单上
                    RecipeResultBean recipeResult1 = RecipeServiceSub.validateRecipeSendDrugMsg(recipe);
                    if (RecipeResultBean.FAIL.equals(recipeResult1.getCode())){
                        rMap.put("signResult", false);
                        rMap.put("errorFlag", true);
                        rMap.put("canContinueFlag", "-1");
                        rMap.put("msg", recipeResult1.getMsg());
                        LOGGER.info("doSignRecipeCheck recipeId={},msg={}",recipeId,rMap.get("msg"));
                        return rMap;
                    }
                    //药企库存实时查询判断药企库存
                    RecipePatientService recipePatientService = ApplicationUtils.getRecipeService(RecipePatientService.class);
                    RecipeResultBean recipeResultBean = recipePatientService.findSupportDepList(0, Arrays.asList(recipeId));
                    if (RecipeResultBean.FAIL.equals(recipeResultBean.getCode())) {
                        rMap.put("signResult", false);
                        rMap.put("errorFlag", true);
                        rMap.put("canContinueFlag", "-1");
                        rMap.put("msg", "很抱歉，当前库存不足无法开处方，请联系客服：" + cacheService.getParam(ParameterConstant.KEY_CUSTOMER_TEL, RecipeSystemConstant.CUSTOMER_TEL));
                        //药品医院有库存的情况
                        LOGGER.info("doSignRecipeCheck recipeId={},msg={}",recipeId,rMap.get("msg"));
                        return rMap;
                    }
                }
                break;
            case 3:
                //药企和医院库存都要校验
                //HIS消息发送
                RecipeResultBean scanResult3 = hisService.scanDrugStockByRecipeId(recipeId);
                boolean checkEnterprise3 = drugsEnterpriseService.checkEnterprise(recipe.getClinicOrgan());
                int errFlag = 0;
                if (checkEnterprise3) {
                    //his管理的药企不要验证库存和配送药品，有his【预校验】校验库存
                    if(new Integer(0).equals(RecipeServiceSub.getOrganEnterprisesDockType(recipe.getClinicOrgan()))){
                        RecipeResultBean recipeResult3 = RecipeServiceSub.validateRecipeSendDrugMsg(recipe);
                        if (RecipeResultBean.FAIL.equals(recipeResult3.getCode())){
                            errFlag = 1;
                            rMap.put("msg", recipeResult3.getError());
                        }else {
                            //药企库存实时查询判断药企库存
                            RecipePatientService recipePatientService = ApplicationUtils.getRecipeService(RecipePatientService.class);
                            RecipeResultBean recipeResultBean = recipePatientService.findSupportDepList(0, Arrays.asList(recipeId));
                            if (RecipeResultBean.FAIL.equals(recipeResultBean.getCode())) {
                                errFlag = 1;
                                rMap.put("msg", recipeResultBean.getError());
                            }
                        }
                    }
                }
                if (RecipeResultBean.FAIL.equals(scanResult3.getCode()) && errFlag == 1) {
                    //医院药企都无库存
                    rMap.put("signResult", false);
                    rMap.put("errorFlag", true);
                    if (recipe.getClinicOrgan() == 1000899) {
                        List<String> nameList = (List<String>) scanResult3.getObject();
                        rMap.put("msg", "【库存不足】由于" + Joiner.on(",").join(nameList) + "门诊药房库存不足，请更换其他药品后再试。");
                    } else {
                        rMap.put("msg", "很抱歉，当前库存不足无法开处方，请联系客服：" + cacheService.getParam(ParameterConstant.KEY_CUSTOMER_TEL, RecipeSystemConstant.CUSTOMER_TEL));
                    }
                    rMap.put("canContinueFlag", "-1");
                    LOGGER.info("doSignRecipeCheck recipeId={},msg={}",recipeId,rMap.get("msg"));
                    return rMap;
                } else if(RecipeResultBean.FAIL.equals(scanResult3.getCode()) && errFlag == 0){
                    //医院有库存药企无库存
                    rMap.put("signResult", false);
                    rMap.put("errorFlag", true);
                    if (recipe.getClinicOrgan() == 1000899) {
                        rMap.put("canContinueFlag", "-1");
                        List<String> nameList = (List<String>) scanResult3.getObject();
                        rMap.put("msg", "【库存不足】由于" + Joiner.on(",").join(nameList) + "门诊药房库存不足，请更换其他药品后再试。");
                    } else {
                        rMap.put("canContinueFlag", "1");
                        rMap.put("msg", "由于该处方单上的药品医院库存不足，该处方仅支持药企配送，无法到院取药，是否继续？");
                    }
                    LOGGER.info("doSignRecipeCheck recipeId={},msg={}",recipeId,rMap.get("msg"));
                    return rMap;
                } else if(RecipeResultBean.SUCCESS.equals(scanResult3.getCode()) && errFlag == 1){
                    //医院无库存药企有库存
                    rMap.put("signResult", false);
                    rMap.put("errorFlag", true);
                    rMap.put("canContinueFlag", "2");
                    rMap.put("msg", "由于该处方单上的药品配送药企库存不足，该处方仅支持到院取药，无法药企配送，是否继续？");
                    LOGGER.info("doSignRecipeCheck recipeId={},msg={}",recipeId,rMap.get("msg"));
                    return rMap;
                }
                break;
        }
        rMap.put("signResult", true);
        rMap.put("errorFlag", false);
        LOGGER.info("doSignRecipeCheck execute ok! rMap:" + JSONUtils.toString(rMap));
        return rMap;
    }

    /**
     * 签名服务（该方法已经已经拆为校验和存储两个子方法）
     *
     * @param recipe  处方
     * @param details 详情
     * @return Map<String ,   Object>
     */
    @RpcService
    public Map<String, Object> doSignRecipe(RecipeBean recipe, List<RecipeDetailBean> details) {
        RecipeDAO recipeDAO = getDAO(RecipeDAO.class);
        RecipeHisService hisService = ApplicationUtils.getRecipeService(RecipeHisService.class);
        DrugsEnterpriseService drugsEnterpriseService = ApplicationUtils.getRecipeService(DrugsEnterpriseService.class);

        Map<String, Object> rMap = Maps.newHashMap();
        PatientDTO patient = patientService.get(recipe.getMpiid());
        //解决旧版本因为wx2.6患者身份证为null，而业务申请不成功
        if (patient == null || StringUtils.isEmpty(patient.getCertificate())) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "该患者还未填写身份证信息，不能开处方");
        }
        // 就诊人改造：为了确保删除就诊人后历史处方不会丢失，加入主账号用户id
        //bug#46436 本人就诊人被删除保存不了导致后续微信模板消息重复推送多次
        List<PatientDTO> requestPatients = patientService.findOwnPatient(patient.getLoginId());
        if (CollectionUtils.isNotEmpty(requestPatients)){
            PatientDTO requestPatient = requestPatients.get(0);
            if (null != requestPatient && null != requestPatient.getMpiId()) {
                recipe.setRequestMpiId(requestPatient.getMpiId());
                // urt用于系统消息推送
                recipe.setRequestUrt(requestPatient.getUrt());
            }
        }
        //如果前端没有传入咨询id则从进行中的复诊或者咨询里取
        //获取咨询单id,有进行中的复诊则优先取复诊，若没有则取进行中的图文咨询
        if (recipe.getClinicId() == null) {
            getConsultIdForRecipeSource(recipe);
        }
        recipe.setStatus(RecipeStatusConstant.UNSIGN);
        recipe.setSignDate(DateTime.now().toDate());
        Integer recipeId = recipe.getRecipeId();
        //如果是已经暂存过的处方单，要去数据库取状态 判断能不能进行签名操作
        if (null != recipeId && recipeId > 0) {
            Integer status = recipeDAO.getStatusByRecipeId(recipeId);
            if (null == status || status > RecipeStatusConstant.UNSIGN) {
                throw new DAOException(ErrorCode.SERVICE_ERROR, "处方单已处理,不能重复签名");
            }

            updateRecipeAndDetail(recipe, details);
        } else {
            recipeId = saveRecipeData(recipe, details);
            recipe.setRecipeId(recipeId);
        }

        //非只能配送处方需要进行医院库存校验
        if (!Integer.valueOf(1).equals(recipe.getDistributionFlag())) {
            //HIS消息发送
            RecipeResultBean scanResult = hisService.scanDrugStockByRecipeId(recipeId);
            if (RecipeResultBean.FAIL.equals(scanResult.getCode())) {
                rMap.put("signResult", false);
                rMap.put("recipeId", recipeId);
                //上海六院无库存不允许开，直接弹出提示
                if (recipe.getClinicOrgan() == 1000899) {
                    //错误信息弹出框，只有 确定  按钮
                    rMap.put("errorFlag", true);
                    List<String> nameList = (List<String>) scanResult.getObject();
                    rMap.put("msg", "【库存不足】由于" + Joiner.on(",").join(nameList) + "门诊药房库存不足，请更换其他药品后再试。");
                    return rMap;
                }
                rMap.put("msg", scanResult.getError());
                if (EXTEND_VALUE_FLAG.equals(scanResult.getExtendValue())) {
                    //这个字段为true，前端展示框内容为msg，走二次确认配送流程调用sendDistributionRecipe
                    rMap.put("scanDrugStock", true);

                }
                return rMap;
            }
        }
        //校验处方药品药企配送以及库存信息
        boolean checkEnterprise = drugsEnterpriseService.checkEnterprise(recipe.getClinicOrgan());
        if (checkEnterprise) {
            //验证能否药品配送以及能否开具到一张处方单上
            RecipeResultBean recipeResult1 = RecipeServiceSub.validateRecipeSendDrugMsg(recipe);
            if (RecipeResultBean.FAIL.equals(recipeResult1.getCode())) {
                rMap.put("signResult", false);
                rMap.put("recipeId", recipeId);
                //错误信息弹出框，只有 确定  按钮
                rMap.put("errorFlag", true);
                rMap.put("msg", recipeResult1.getMsg());
                //药品医院有库存的情况
                if (!Integer.valueOf(1).equals(recipe.getDistributionFlag())) {
                    //错误信息弹出框，能否继续标记----点击是可以继续开方
                    rMap.put("canContinueFlag", true);
                    rMap.put("msg", recipeResult1.getMsg() + "，仅支持到院取药，是否继续开方？");
                }
                LOGGER.info("doSignRecipe recipeId={},msg={}", recipeId, rMap.get("msg"));
                return rMap;
            }
            //药企库存实时查询
            RecipePatientService recipePatientService = ApplicationUtils.getRecipeService(RecipePatientService.class);
            //判断药企库存
            RecipeResultBean recipeResultBean = recipePatientService.findSupportDepList(0, Arrays.asList(recipeId));
            /*RecipeResultBean recipeResultBean = scanStockForOpenRecipe(recipeId);*/
            if (RecipeResultBean.FAIL.equals(recipeResultBean.getCode())) {
                rMap.put("signResult", false);
                rMap.put("recipeId", recipeId);
                //错误信息弹出框，只有 确定  按钮
                rMap.put("errorFlag", true);
                rMap.put("msg", "很抱歉，当前库存不足无法开处方，请联系客服：" + cacheService.getParam(ParameterConstant.KEY_CUSTOMER_TEL, RecipeSystemConstant.CUSTOMER_TEL));
                //药品医院有库存的情况
                if (!Integer.valueOf(1).equals(recipe.getDistributionFlag())) {
                    //错误信息弹出框，能否继续标记----点击是可以继续开方
                    rMap.put("canContinueFlag", true);
                    rMap.put("msg", "由于该处方单上的药品配送药企库存不足，该处方仅支持到院取药，无法药企配送，是否继续？");
                }
                LOGGER.info("doSignRecipe recipeId={},msg={}", recipeId, rMap.get("msg"));
                return rMap;
            }
        }
        //发送his前更新处方状态---医院确认中
        recipeDAO.updateRecipeInfoByRecipeId(recipeId, RecipeStatusConstant.CHECKING_HOS, null);
        //HIS消息发送--异步处理
        /*boolean result = hisService.recipeSendHis(recipeId, null);*/
        RecipeBusiThreadPool.submit(new PushRecipeToHisCallable(recipeId));
        rMap.put("signResult", true);
        rMap.put("recipeId", recipeId);
        rMap.put("consultId", recipe.getClinicId());
        rMap.put("errorFlag", false);
        LOGGER.info("doSignRecipe execute ok! rMap:" + JSONUtils.toString(rMap));
        return rMap;
    }

    /**
     * 当药企无法配送只能到院取药时--继续签名方法--医生APP、医生PC-----根据canContinueFlag判断
     *
     * @return Map<String, Object>
     */
    @RpcService
    public Map<String, Object> doSignRecipeContinue(Integer recipeId) {
        RecipeDAO recipeDAO = getDAO(RecipeDAO.class);
        Recipe recipe = recipeDAO.getByRecipeId(recipeId);
        //发送his前更新处方状态---医院确认中
        recipeDAO.updateRecipeInfoByRecipeId(recipeId, RecipeStatusConstant.CHECKING_HOS, ImmutableMap.of("distributionFlag", 2));
        //HIS消息发送--异步处理
        /*boolean result = hisService.recipeSendHis(recipeId, null);*/
        RecipeBusiThreadPool.submit(new PushRecipeToHisCallable(recipeId));
        //更新保存智能审方信息
        PrescriptionService prescriptionService = ApplicationUtils.getRecipeService(PrescriptionService.class);
        if (prescriptionService.getIntellectJudicialFlag(recipe.getClinicOrgan()) == 1) {
            RecipeDetailDAO recipeDetailDAO = getDAO(RecipeDetailDAO.class);
            List<Recipedetail> recipedetails = recipeDetailDAO.findByRecipeId(recipeId);
            //更新审方信息
            RecipeBusiThreadPool.execute(new SaveAutoReviewRunable(ObjectCopyUtils.convert(recipe, RecipeBean.class), ObjectCopyUtils.convert(recipedetails, RecipeDetailBean.class)));
        }
        //健康卡数据上传
        RecipeBusiThreadPool.execute(new CardDataUploadRunable(recipe.getClinicOrgan(), recipe.getMpiid(),"010106"));
        Map<String, Object> rMap = Maps.newHashMap();
        rMap.put("signResult", true);
        rMap.put("recipeId", recipeId);
        rMap.put("consultId", recipe.getClinicId());
        rMap.put("errorFlag", false);
        LOGGER.info("doSignRecipe execute ok! rMap:" + JSONUtils.toString(rMap));
        return rMap;
    }


    @RpcService
    public void getConsultIdForRecipeSource(RecipeBean recipe) {
        //根据申请人mpiid，requestMode 获取当前咨询单consultId
        //如果没有进行中的复诊就取进行中的咨询否则没有
        IConsultService iConsultService = ApplicationUtils.getConsultService(IConsultService.class);
        IRevisitService iRevisitService = RevisitAPI.getService(IRevisitService.class);
        //获取在线复诊
        List<Integer> consultIds = iRevisitService.findApplyingConsultByRequestMpiAndDoctorId(recipe.getRequestMpiId(), recipe.getDoctor(), RecipeSystemConstant.CONSULT_TYPE_RECIPE);
        Integer consultId = null;
        if (CollectionUtils.isNotEmpty(consultIds)) {
            consultId = consultIds.get(0);
            recipe.setBussSource(2);
        } else {
            //图文咨询
            consultIds = iConsultService.findApplyingConsultByRequestMpiAndDoctorId(recipe.getRequestMpiId(), recipe.getDoctor(), RecipeSystemConstant.CONSULT_TYPE_GRAPHIC);
            if (CollectionUtils.isNotEmpty(consultIds)) {
                consultId = consultIds.get(0);
                recipe.setBussSource(1);
            }
        }
        recipe.setClinicId(consultId);
    }

    /**
     * 修改处方
     *
     * @param recipeBean     处方对象
     * @param detailBeanList 处方详情
     */
    @RpcService
    public Integer updateRecipeAndDetail(RecipeBean recipeBean, List<RecipeDetailBean> detailBeanList) {
        if (recipeBean == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "recipe is required!");
        }
        Integer recipeId = recipeBean.getRecipeId();
        if (recipeId == null || recipeId <= 0) {
            throw new DAOException(DAOException.VALUE_NEEDED, "recipeId is required!");
        }
        RecipeDAO recipeDAO = getDAO(RecipeDAO.class);
        RecipeDetailDAO recipeDetailDAO = getDAO(RecipeDetailDAO.class);

        Recipe recipe = ObjectCopyUtils.convert(recipeBean, Recipe.class);

        Recipe dbRecipe = recipeDAO.getByRecipeId(recipeId);
        if (null == dbRecipe.getStatus() || dbRecipe.getStatus() > RecipeStatusConstant.UNSIGN) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "该处方单不是新处方或者审核失败的处方，不能修改");
        }

        int beforeStatus = dbRecipe.getStatus();
        List<Recipedetail> recipedetails = ObjectCopyUtils.convert(detailBeanList, Recipedetail.class);
        if (null != detailBeanList && detailBeanList.size() > 0) {
            if (null == recipedetails) {
                recipedetails = new ArrayList<>(0);
            }
        }

        RecipeServiceSub.setRecipeMoreInfo(recipe, recipedetails, recipeBean, 1);
        //将原先处方单详情的记录都置为无效 status=0
        recipeDetailDAO.updateDetailInvalidByRecipeId(recipeId);
        Integer dbRecipeId = recipeDAO.updateOrSaveRecipeAndDetail(recipe, recipedetails, true);

        //武昌需求，加入处方扩展信息
        RecipeExtendBean recipeExt = recipeBean.getRecipeExtend();
        if (null != recipeExt && null != dbRecipeId) {
            RecipeExtend recipeExtend = ObjectCopyUtils.convert(recipeExt, RecipeExtend.class);
            recipeExtend.setRecipeId(dbRecipeId);
            //老的字段兼容处理
            if (StringUtils.isNotEmpty(recipeExtend.getPatientType())) {
                recipeExtend.setMedicalType(recipeExtend.getPatientType());
                switch (recipeExtend.getPatientType()) {
                    case "2":
                        recipeExtend.setMedicalTypeText(("普通医保"));
                        break;
                    case "3":
                        recipeExtend.setMedicalTypeText(("慢病医保"));
                        break;
                    default:
                }
            }
            //慢病开关
            if (recipeExtend.getRecipeChooseChronicDisease() == null) {
                try {
                    IConfigurationCenterUtilsService configurationService = ApplicationUtils.getBaseService(IConfigurationCenterUtilsService.class);
                    Integer recipeChooseChronicDisease = (Integer) configurationService.getConfiguration(recipeBean.getClinicOrgan(), "recipeChooseChronicDisease");
                    recipeExtend.setRecipeChooseChronicDisease(recipeChooseChronicDisease);
                } catch (Exception e) {
                    LOGGER.error("doWithRecipeExtend 获取开关异常", e);
                }
            }

            emrRecipeManager.updateMedicalInfo(recipeBean, recipeExtend);
            RecipeExtendDAO recipeExtendDAO = DAOFactory.getDAO(RecipeExtendDAO.class);
            recipeExtendDAO.saveOrUpdateRecipeExtend(recipeExtend);
        }
        //记录日志
        RecipeLogService.saveRecipeLog(dbRecipeId, beforeStatus, beforeStatus, "修改处方单");
        return dbRecipeId;
    }

    public void setMergeDrugType(List<Recipedetail> recipedetails, Recipe dbRecipe) {
        //date  20200529 JRK
        //根据配置项重新设置处方类型和处方药品详情属性类型
        IConfigurationCenterUtilsService configurationService = ApplicationUtils.getBaseService(IConfigurationCenterUtilsService.class);
        DrugListDAO drugListDAO = DAOFactory.getDAO(DrugListDAO.class);
        boolean isMergeRecipeType = (null != configurationService.getConfiguration(dbRecipe.getClinicOrgan(), "isMergeRecipeType")) ? (Boolean) configurationService.getConfiguration(dbRecipe.getClinicOrgan(), "isMergeRecipeType") : false;
        //允许中西药合并
        DrugList nowDrugList;
        if (isMergeRecipeType) {
            if (CollectionUtils.isNotEmpty(recipedetails)) {
                nowDrugList = drugListDAO.getById(recipedetails.get(0).getDrugId());
                dbRecipe.setRecipeType(null != nowDrugList ? nowDrugList.getDrugType() : null);
                for (Recipedetail recipedetail : recipedetails) {
                    nowDrugList = drugListDAO.getById(recipedetail.getDrugId());
                    recipedetail.setDrugType(null != nowDrugList ? nowDrugList.getDrugType() : null);
                }
            }

        }
    }

    /**
     * 新版签名服务
     *
     * @param recipeBean     处方
     * @param detailBeanList 详情
     * @return Map<String, Object>
     * @paran consultId  咨询单Id
     */
    @RpcService
    public Map<String, Object> doSignRecipeExt(RecipeBean recipeBean, List<RecipeDetailBean> detailBeanList) {
        LOGGER.info("doSignRecipeExt param: recipeBean={} detailBean={}", JSONUtils.toString(recipeBean), JSONUtils.toString(detailBeanList));
        //将密码放到redis中
        redisClient.set("caPassword", recipeBean.getCaPassword());
        Map<String, Object> rMap = null;
        try {
            //上海肺科个性化处理--智能审方重要警示弹窗处理
            doforShangHaiFeiKe(recipeBean, detailBeanList);
            rMap = doSignRecipe(recipeBean, detailBeanList);
            //获取处方签名结果
            Boolean result = Boolean.parseBoolean(rMap.get("signResult").toString());
            if (result) {
                //非可使用省医保的处方立即发送处方卡片，使用省医保的处方需要在药师审核通过后显示
                if (!recipeBean.canMedicalPay()) {
                    //发送卡片
                    Recipe recipe = ObjectCopyUtils.convert(recipeBean, Recipe.class);
                    List<Recipedetail> details = ObjectCopyUtils.convert(detailBeanList, Recipedetail.class);
                    RecipeServiceSub.sendRecipeTagToPatient(recipe, details, rMap, false);
                }
                //个性化医院特殊处理，开完处方模拟his成功返回数据（假如前置机不提供默认返回数据）
                doHisReturnSuccessForOrgan(recipeBean, rMap);
            }
            PrescriptionService prescriptionService = ApplicationUtils.getRecipeService(PrescriptionService.class);
            if (prescriptionService.getIntellectJudicialFlag(recipeBean.getClinicOrgan()) == 1) {
                //更新审方信息
                RecipeBusiThreadPool.execute(new SaveAutoReviewRunable(recipeBean, detailBeanList));
            }
            //健康卡数据上传
            RecipeBusiThreadPool.execute(new CardDataUploadRunable(recipeBean.getClinicOrgan(), recipeBean.getMpiid(),"010106"));
        } catch (Exception e) {
            LOGGER.error("doSignRecipeExt error", e);
            throw new DAOException(recipe.constant.ErrorCode.SERVICE_ERROR, e.getMessage());
        }
        LOGGER.info("doSignRecipeExt execute ok! rMap:" + JSONUtils.toString(rMap));
        return rMap;
    }

    public void doforShangHaiFeiKe(RecipeBean recipe, List<RecipeDetailBean> details) {
        ////上海医院个性化处理--智能审方重要警示弹窗处理--为了测评-可配置
        Set<String> organIdList = redisClient.sMembers(CacheConstant.KEY_AUDIT_TIP_LIST);
        if ((organIdList != null && organIdList.contains(recipe.getClinicOrgan().toString())) ||
                recipe.getClinicOrgan() == 1002902) {//上海肺科
            PrescriptionService prescriptionService = ApplicationUtils.getRecipeService(PrescriptionService.class);
            AutoAuditResultBean autoAuditResult = prescriptionService.analysis(recipe, details);
            List<PAWebMedicinesBean> paResultList = autoAuditResult.getMedicines();
            if (CollectionUtils.isNotEmpty(paResultList)) {
                List<IssueBean> issueList;
                for (PAWebMedicinesBean paMedicine : paResultList) {
                    issueList = paMedicine.getIssues();
                    if (CollectionUtils.isNotEmpty(issueList)) {
                        for (IssueBean issue : issueList) {
                            if ("RL001".equals(issue.getLvlCode())) {
                                throw new DAOException(609, issue.getDetail());
                            }
                        }
                    }
                }
            }
        }
    }

    public void doHisReturnSuccessForOrgan(RecipeBean recipeBean, Map<String, Object> rMap) {
        Set<String> organIdList = redisClient.sMembers(CacheConstant.KEY_SKIP_HISRECIPE_LIST);
        if (organIdList != null && organIdList.contains(recipeBean.getClinicOrgan().toString())) {
            RecipeBusiThreadPool.submit(new Callable() {
                @Override
                public Object call() throws Exception {
                    PatientService patientService = BasicAPI.getService(PatientService.class);
                    PatientDTO patientDTO = patientService.getPatientByMpiId(recipeBean.getMpiid());
                    Date now = DateTime.now().toDate();
                    String str = "";
                    if (patientDTO != null && StringUtils.isNotEmpty(patientDTO.getCertificate())) {
                        str = patientDTO.getCertificate().substring(patientDTO.getCertificate().length() - 5);
                    }
                    RecipeToHisCallbackService service = ApplicationUtils.getRecipeService(RecipeToHisCallbackService.class);
                    HisSendResTO response = new HisSendResTO();
                    response.setRecipeId(((Integer) rMap.get("recipeId")).toString());
                    List<OrderRepTO> repList = Lists.newArrayList();
                    OrderRepTO orderRepTO = new OrderRepTO();
                    //门诊号处理 年月日+患者身份证后5位 例：2019060407915
                    orderRepTO.setPatientID(DateConversion.getDateFormatter(now, "yyMMdd") + str);
                    orderRepTO.setRegisterID(orderRepTO.getPatientID());
                    //生成处方编号，不需要通过HIS去产生
                    String recipeCodeStr = DigestUtil.md5For16(recipeBean.getClinicOrgan() + recipeBean.getMpiid() + Calendar.getInstance().getTimeInMillis());
                    orderRepTO.setRecipeNo(recipeCodeStr);
                    repList.add(orderRepTO);
                    response.setData(repList);
                    service.sendSuccess(response);
                    return null;
                }
            });
        }
    }

    /**
     * 处方二次签名
     *
     * @param recipe
     * @return
     */
    @RpcService
    public RecipeResultBean doSecondSignRecipe(RecipeBean recipe) {
        RecipeResultBean resultBean = RecipeResultBean.getSuccess();
        RecipeDAO recipeDAO = getDAO(RecipeDAO.class);
        RecipeService recipeService = ApplicationUtils.getRecipeService(RecipeService.class);

        Recipe dbRecipe = RecipeValidateUtil.checkRecipeCommonInfo(recipe.getRecipeId(), resultBean);
        if (null == dbRecipe) {
            LOGGER.error("validateDrugs 平台无该处方对象. recipeId=[{}] error={}", recipe.getRecipeId(), JSONUtils.toString(resultBean));
            return resultBean;
        }

        Integer status = dbRecipe.getStatus();
        if (null == status || status != RecipeStatusConstant.CHECK_NOT_PASS_YS) {
            resultBean.setCode(RecipeResultBean.FAIL);
            resultBean.setMsg("该处方不是审核未通过状态");
            return resultBean;
        }

        Integer afterStatus = RecipeStatusConstant.CHECK_PASS_YS;
        //添加后置状态设置
        if (ReviewTypeConstant.Postposition_Check == dbRecipe.getReviewType()) {
            if (!dbRecipe.canMedicalPay()) {
                RecipeOrderDAO orderDAO = getDAO(RecipeOrderDAO.class);
                boolean effective = orderDAO.isEffectiveOrder(dbRecipe.getOrderCode(), dbRecipe.getPayMode());
                if (null != recipe.getOrderCode() && !effective) {
                    resultBean.setCode(RecipeResultBean.FAIL);
                    resultBean.setMsg("该处方已失效");
                    return resultBean;
                }
            } else {
                afterStatus = RecipeStatusConstant.CHECK_PASS;
            }
        } else if (ReviewTypeConstant.Preposition_Check == dbRecipe.getReviewType()) {
            afterStatus = RecipeStatusConstant.CHECK_PASS;
        }
        if (!dbRecipe.canMedicalPay()) {
            RecipeOrderDAO orderDAO = getDAO(RecipeOrderDAO.class);
            boolean effective = orderDAO.isEffectiveOrder(dbRecipe.getOrderCode(), dbRecipe.getPayMode());
            if (null != recipe.getOrderCode() && !effective) {
                resultBean.setCode(RecipeResultBean.FAIL);
                resultBean.setMsg("该处方已失效");
                return resultBean;
            }
        } else {
            afterStatus = RecipeStatusConstant.CHECK_PASS;
        }
        Map<String, Object> updateMap = new HashMap<>();
        updateMap.put("supplementaryMemo", recipe.getSupplementaryMemo());
        updateMap.put("checkStatus", RecipecCheckStatusConstant.Check_Normal);

        //date 20190929
        //这里提示文案描述，扩展成二次审核通过/二次审核不通过的说明
        recipeDAO.updateRecipeInfoByRecipeId(recipe.getRecipeId(), afterStatus, updateMap);
        afterCheckPassYs(dbRecipe);
        //date20200227 判断前置的时候二次签名成功，发对应的消息
        if (ReviewTypeConstant.Preposition_Check == dbRecipe.getReviewType()) {
            auditModeContext.getAuditModes(dbRecipe.getReviewType()).afterCheckPassYs(dbRecipe);
        }

        try {
            //生成pdf并签名
            recipeService.generateRecipePdfAndSign(recipe.getRecipeId());
        } catch (Exception e) {
            LOGGER.error("doSecondSignRecipe 签名失败. recipeId=[{}], error={}", recipe.getRecipeId(), e.getMessage(), e);
        }

        LOGGER.info("doSecondSignRecipe execute ok! ");
        return resultBean;
    }

    /**
     * 处方药师审核通过后处理
     *
     * @param recipe
     * @return
     */
    @RpcService
    public RecipeResultBean afterCheckPassYs(Recipe recipe) {
        if (null == recipe) {
            return null;
        }
        RecipeDetailDAO detailDAO = getDAO(RecipeDetailDAO.class);
        RecipeOrderService orderService = ApplicationUtils.getRecipeService(RecipeOrderService.class);
        RemoteDrugEnterpriseService service = ApplicationUtils.getRecipeService(RemoteDrugEnterpriseService.class);

        RecipeResultBean resultBean = RecipeResultBean.getSuccess();
        Integer recipeId = recipe.getRecipeId();

        //正常平台处方
        if (RecipeBussConstant.FROMFLAG_PLATFORM.equals(recipe.getFromflag())) {
            if (ReviewTypeConstant.Postposition_Check == recipe.getReviewType()) {
                if (recipe.canMedicalPay()) {
                    //如果是可医保支付的单子，审核通过之后是变为待处理状态，需要用户支付完成才发往药企
                    RecipeServiceSub.sendRecipeTagToPatient(recipe, detailDAO.findByRecipeId(recipeId), null, true);
                    //向患者推送处方消息
                    RecipeMsgService.batchSendMsg(recipe, RecipeStatusConstant.CHECK_PASS);
                } else {
                    //date:20190920
                    //审核通过后设置订单的状态（后置）
                    PurchaseService purchaseService = ApplicationUtils.getRecipeService(PurchaseService.class);
                    Integer status = purchaseService.getOrderStatus(recipe);
                    orderService.updateOrderInfo(recipe.getOrderCode(), ImmutableMap.of("status", status), resultBean);
                    //发送患者审核完成消息
                    RecipeMsgService.batchSendMsg(recipe, RecipeStatusConstant.CHECK_PASS_YS);

                    //6.24 货到付款或者药店取药也走药师审核通过推送处方信息
                    // 平台处方发送药企处方信息
                    service.pushSingleRecipeInfo(recipeId);
                }
            }
            //date 2019/10/17
            //添加审核后的有库存无库存的消息平台逻辑
            //消息发送：平台处方且购药方式药店取药
            if (RecipeBussConstant.RECIPEMODE_NGARIHEALTH.equals(recipe.getRecipeMode()) && RecipeBussConstant.GIVEMODE_TFDS.equals(recipe.getGiveMode())) {
                //此处增加药店取药消息推送
                RemoteDrugEnterpriseService remoteDrugService = ApplicationUtils.getRecipeService(RemoteDrugEnterpriseService.class);
                DrugsEnterpriseDAO drugsEnterpriseDAO = DAOFactory.getDAO(DrugsEnterpriseDAO.class);
                if (recipe.getEnterpriseId() == null) {
                    LOGGER.info("审方后置-药店取药-药企为空");
                } else {
                    DrugsEnterprise drugsEnterprise = drugsEnterpriseDAO.getById(recipe.getEnterpriseId());
                    DrugEnterpriseResult result = remoteDrugService.scanStock(recipeId, drugsEnterprise);
                    boolean scanFlag = result.getCode().equals(DrugEnterpriseResult.SUCCESS) ? true : false;
                    LOGGER.info("AuditPostMode afterCheckPassYs scanFlag:{}.", scanFlag);
                    if (scanFlag) {
                        //表示需要进行库存校验并且有库存
                        RecipeMsgService.sendRecipeMsg(RecipeMsgEnum.RECIPE_DRUG_HAVE_STOCK, recipe);
                    } else if (drugsEnterprise.getCheckInventoryFlag() == 2) {
                        //表示无库存但是药店可备货
                        RecipeMsgService.sendRecipeMsg(RecipeMsgEnum.RECIPE_DRUG_NO_STOCK_READY, recipe);
                    }
                }
            }

        } else if (RecipeBussConstant.FROMFLAG_HIS_USE.equals(recipe.getFromflag())) {
            Integer status = OrderStatusConstant.READY_SEND;
            if (RecipeBussConstant.PAYMODE_TFDS.equals(recipe.getPayMode())) {
                status = OrderStatusConstant.READY_GET_DRUG;
                // HOS处方发送药企处方信息
                service.pushSingleRecipeInfo(recipeId);
                //发送审核成功消息
                //${sendOrgan}：您的处方已审核通过，请于${expireDate}前到${pharmacyName}取药，地址：${addr}。如有疑问，请联系开方医生或拨打${customerTel}联系小纳。
                RecipeMsgService.sendRecipeMsg(RecipeMsgEnum.RECIPE_YS_CHECKPASS_4TFDS, recipe);
            } else if (RecipeBussConstant.PAYMODE_ONLINE.equals(recipe.getPayMode())) {
                // HOS处方发送药企处方信息
                service.pushSingleRecipeInfo(recipeId);
                //发送审核成功消息
                //${sendOrgan}：您的处方已审核通过，我们将以最快的速度配送到：${addr}。如有疑问，请联系开方医生或拨打${customerTel}联系小纳。
                RecipeMsgService.sendRecipeMsg(RecipeMsgEnum.RECIPE_YS_CHECKPASS_4STH, recipe);
            } else if (RecipeBussConstant.PAYMODE_TO_HOS.equals(recipe.getPayMode())) {
                status = OrderStatusConstant.READY_GET_DRUG;
            } else {
                status = OrderStatusConstant.READY_GET_DRUG;
                // HOS处方发送药企处方信息，由于是自由选择，所以匹配到的药企都发送一遍
                RecipeService recipeService = ApplicationUtils.getRecipeService(RecipeService.class);
                List<DrugsEnterprise> depList = recipeService.findSupportDepList(Lists.newArrayList(recipeId), recipe.getClinicOrgan(), null, false, null);
                LOGGER.info("afterCheckPassYs recipeId={}, 匹配到药企数量[{}]", recipeId, depList.size());
                for (DrugsEnterprise dep : depList) {
                    service.pushSingleRecipeInfoWithDepId(recipeId, dep.getId());
                }

                //自由选择消息发送
                //${sendOrgan}：您的处方已通过药师审核，请联系开方医生选择取药方式并支付处方费用。如有疑问，请拨打${customerTel}联系小纳。
                RecipeMsgService.sendRecipeMsg(RecipeMsgEnum.RECIPE_YS_CHECKPASS_4FREEDOM, recipe);
            }

            orderService.updateOrderInfo(recipe.getOrderCode(), ImmutableMap.of("status", status), resultBean);
        }

        RecipeLogService.saveRecipeLog(recipe.getRecipeId(), recipe.getStatus(), recipe.getStatus(), "审核通过处理完成");
        return resultBean;
    }

    /**
     * 药师审核不通过后处理
     *
     * @param recipe
     */
    public void afterCheckNotPassYs(Recipe recipe) {
        if (null == recipe) {
            return;
        }
        RecipeOrderDAO orderDAO = getDAO(RecipeOrderDAO.class);
        boolean effective = orderDAO.isEffectiveOrder(recipe.getOrderCode(), recipe.getPayMode());
        //是否是有效订单
        if (!effective) {
            return;
        }
        RecipeOrderService orderService = ApplicationUtils.getRecipeService(RecipeOrderService.class);
        //相应订单处理
        orderService.cancelOrderByRecipeId(recipe.getRecipeId(), OrderStatusConstant.CANCEL_NOT_PASS);

        //根据付款方式提示不同消息
        //date 2019/10/14
        //逻辑修改成，退款的不筛选支付方式
        if (PayConstant.PAY_FLAG_PAY_SUCCESS == recipe.getPayFlag()) {
            //线上支付
            //微信退款
            wxPayRefundForRecipe(2, recipe.getRecipeId(), null);
        }
    }

    /**
     * 医院处方审核 (当前为自动审核通过)
     *
     * @param recipeId
     * @return HashMap<String, Object>
     */
    @RpcService
    @Deprecated
    public HashMap<String, Object> recipeAutoCheck(Integer recipeId) {
        LOGGER.info("recipeAutoCheck get in recipeId=" + recipeId);
        HashMap<String, Object> map = Maps.newHashMap();
        RecipeDAO recipeDAO = getDAO(RecipeDAO.class);
        Recipe recipe = recipeDAO.getByRecipeId(recipeId);
        Integer recipeStatus = recipe.getStatus();
        if (RecipeStatusConstant.UNCHECK == recipeStatus) {
            int afterStatus = RecipeStatusConstant.CHECK_PASS;
            Map<String, Object> attrMap = Maps.newHashMap();
            attrMap.put("checkDate", DateTime.now().toDate());
            attrMap.put("checkOrgan", recipe.getClinicOrgan());
            attrMap.put("checker", 0);
            attrMap.put("checkFailMemo", "");
            recipeDAO.updateRecipeInfoByRecipeId(recipeId, afterStatus, attrMap);

            RecipeLogService.saveRecipeLog(recipeId, recipeStatus, afterStatus, "自动审核通过");

            map.put("code", RecipeSystemConstant.SUCCESS);
            map.put("msg", "");

            recipe.setStatus(afterStatus);
            //审核失败的话需要发送信息
//            RecipeMsgService.batchSendMsg(recipe,RecipeStatusConstant.CHECK_NOT_PASS);
        } else {
            map.put("code", RecipeSystemConstant.FAIL);
            map.put("msg", "处方单不是待审核状态，不能进行自动审核");
        }

        // 医院审核系统的对接

        return map;
    }

    /**
     * 处方单详情服务
     *
     * @param recipeId 处方ID
     * @return HashMap<String, Object>
     */
    @RpcService
    public Map<String, Object> findRecipeAndDetailById(int recipeId) {
        Map<String, Object> result = RecipeServiceSub.getRecipeAndDetailByIdImpl(recipeId, true);
        PatientDTO patient = (PatientDTO) result.get("patient");
        result.put("patient", ObjectCopyUtils.convert(patient, PatientVO.class));
        try {
            EmrRecipeManager.getMedicalInfo((RecipeBean) result.get("recipe"), (RecipeExtend) result.get("recipeExtend"));
        } catch (Exception e) {
            LOGGER.error("emrRecipeManager getMedicalInfo is error ", e);
        }
        return result;
    }

    /**
     * 获取智能审方结果详情
     *
     * @param recipeId 处方ID
     * @return
     */
    @RpcService
    public List<AuditMedicinesBean> getAuditMedicineIssuesByRecipeId(int recipeId) {
        return RecipeServiceSub.getAuditMedicineIssuesByRecipeId(recipeId);
    }

    /**
     * 处方撤销方法(供医生端使用)---无撤销原因时调用保留为了兼容---新方法在RecipeCancelService里
     *
     * @param recipeId 处方Id
     * @return Map<String, Object>
     * 撤销成功返回 {"result":true,"msg":"处方撤销成功"}
     * 撤销失败返回 {"result":false,"msg":"失败原因"}
     */
    @RpcService
    public Map<String, Object> cancelRecipe(Integer recipeId) {
        return RecipeServiceSub.cancelRecipeImpl(recipeId, 0, "", "");
    }

    /**
     * 处方撤销方法(供运营平台使用)
     *
     * @param recipeId
     * @param name     操作人员姓名
     * @param message  处方撤销原因
     * @return
     */
    @RpcService
    public Map<String, Object> cancelRecipeForOperator(Integer recipeId, String name, String message) {
        return RecipeServiceSub.cancelRecipeImpl(recipeId, 1, name, message);
    }


    /**
     * 定时任务:同步HIS医院药品信息 每天凌晨1点同步
     */
    @RpcService(timeout = 600000)
    public void drugInfoSynTask() {
        drugInfoSynTaskExt(null);
    }

    @RpcService(timeout = 600000)
    public void drugInfoSynTaskExt(Integer organId) {
        RecipeHisService hisService = ApplicationUtils.getRecipeService(RecipeHisService.class);
        IOrganConfigService iOrganConfigService = ApplicationUtils.getBaseService(IOrganConfigService.class);

        List<Integer> organIds = new ArrayList<>();
        if (null == organId) {
            //查询 base_organconfig 表配置需要同步的机构
            //todo--这个配置要优化到运营平台机构配置中
            organIds = iOrganConfigService.findEnableDrugSync();
        } else {
            organIds.add(organId);
        }

        if (CollectionUtils.isEmpty(organIds)) {
            LOGGER.info("drugInfoSynTask organIds is empty.");
            return;
        }

        OrganDrugListDAO organDrugListDAO = getDAO(OrganDrugListDAO.class);
        Long updateNum = 0L;

        for (int oid : organIds) {
            //获取纳里机构药品目录
            List<OrganDrugList> details = organDrugListDAO.findOrganDrugByOrganId(oid);
            if (CollectionUtils.isEmpty(details)) {
                LOGGER.info("drugInfoSynTask 当前医院organId=[{}]，平台没有匹配到机构药品.", oid);
                continue;
            }
            Map<String, OrganDrugList> drugMap = details.stream().collect(Collectors.toMap(OrganDrugList::getOrganDrugCode, a -> a, (k1, k2) -> k1));
            //查询起始下标
            int startIndex = 0;
            boolean finishFlag = true;
            long total = organDrugListDAO.getTotal(oid);
            while (finishFlag) {
                List<DrugInfoTO> drugInfoList = hisService.getDrugInfoFromHis(oid, false, startIndex);
                if (!CollectionUtils.isEmpty(drugInfoList)) {
                    //是否有效标志 1-有效 0-无效
                    for (DrugInfoTO drug : drugInfoList) {
                        OrganDrugList organDrug = drugMap.get(drug.getDrcode());
                        if (null == organDrug) {
                            continue;
                        }
                        updateHisDrug(drug, organDrug);
                        updateNum++;
                        LOGGER.info("drugInfoSynTask organId=[{}] drug=[{}]", oid, JSONUtils.toString(drug));
                    }
                }
                startIndex++;
                if (startIndex >= total){
                    LOGGER.info("drugInfoSynTask organId=[{}] 本次查询量：total=[{}] ,总更新量：update=[{}]，药品信息更新结束.", oid, startIndex, updateNum);
                    finishFlag = false;
                }
            }
        }
    }

    /**
     * 定时任务:定时取消处方单
     */
    @RpcService
    public void cancelRecipeTask() {
        RecipeOrderDAO orderDAO = getDAO(RecipeOrderDAO.class);
        RecipeDAO recipeDAO = getDAO(RecipeDAO.class);
        RecipeOrderService orderService = ApplicationUtils.getRecipeService(RecipeOrderService.class);
        RecipeOrderDAO recipeOrderDAO = getDAO(RecipeOrderDAO.class);
        RecipeHisService hisService = ApplicationUtils.getRecipeService(RecipeHisService.class);

        List<Integer> statusList = Arrays.asList(RecipeStatusConstant.NO_PAY, RecipeStatusConstant.NO_OPERATOR);
        StringBuilder memo = new StringBuilder();
        RecipeOrder order;
        //设置查询时间段
        String endDt = DateConversion.getDateFormatter(DateConversion.getDateTimeDaysAgo(Integer.parseInt(cacheService.getParam(ParameterConstant.KEY_RECIPE_VALIDDATE_DAYS, RECIPE_EXPIRED_DAYS.toString()))), DateConversion.DEFAULT_DATE_TIME);
        String startDt = DateConversion.getDateFormatter(DateConversion.getDateTimeDaysAgo(Integer.parseInt(cacheService.getParam(ParameterConstant.KEY_RECIPE_CANCEL_DAYS, RECIPE_EXPIRED_SEARCH_DAYS.toString()))), DateConversion.DEFAULT_DATE_TIME);

        //增加订单未取药推送
        List<String> orderCodes = recipeOrderDAO.getRecipeIdForCancelRecipeOrder(startDt, endDt);
        if (CollectionUtils.isNotEmpty(orderCodes)) {
            List<Recipe> recipes = recipeDAO.getRecipeListByOrderCodes(orderCodes);
            LOGGER.info("cancelRecipeOrderTask , 取消数量=[{}], 详情={}", recipes.size(), JSONUtils.toString(recipes));
            for (Recipe recipe : recipes) {
                memo.delete(0, memo.length());
                int recipeId = recipe.getRecipeId();
                //相应订单处理
                order = orderDAO.getOrderByRecipeId(recipeId);
                orderService.cancelOrder(order, OrderStatusConstant.CANCEL_AUTO, true);
                //变更处方状态
                recipeDAO.updateRecipeInfoByRecipeId(recipeId, RecipeStatusConstant.NO_OPERATOR, ImmutableMap.of("chooseFlag", 1));
                RecipeMsgService.batchSendMsg(recipe, RecipeStatusConstant.RECIPE_ORDER_CACEL);
                memo.append("已取消,超过3天未操作");
                //HIS消息发送
                boolean succFlag = hisService.recipeStatusUpdate(recipeId);
                if (succFlag) {
                    memo.append(",HIS推送成功");
                } else {
                    memo.append(",HIS推送失败");
                }
                //保存处方状态变更日志
                RecipeLogService.saveRecipeLog(recipeId, RecipeStatusConstant.CHECK_PASS, RecipeStatusConstant.NO_OPERATOR, memo.toString());
            }
            //修改cdr_his_recipe status为已处理
            orderService.updateHisRecieStatus(recipes);
        }
        for (Integer status : statusList) {
            List<Recipe> recipeList = recipeDAO.getRecipeListForCancelRecipe(status, startDt, endDt);
            LOGGER.info("cancelRecipeTask 状态=[{}], 取消数量=[{}], 详情={}", status, recipeList.size(), JSONUtils.toString(recipeList));
            if (CollectionUtils.isNotEmpty(recipeList)) {
                for (Recipe recipe : recipeList) {
                    //过滤掉流转到扁鹊处方流转平台的处方
                    if (RecipeServiceSub.isBQEnterpriseBydepId(recipe.getEnterpriseId())){
                        continue;
                    }
                    if (RecipeBussConstant.RECIPEMODE_ZJJGPT.equals(recipe.getRecipeMode())) {
                        OrganAndDrugsepRelationDAO organAndDrugsepRelationDAO = DAOFactory.getDAO(OrganAndDrugsepRelationDAO.class);
                        List<DrugsEnterprise> drugsEnterprises = organAndDrugsepRelationDAO.findDrugsEnterpriseByOrganIdAndStatus(recipe.getClinicOrgan(), 1);
                        for (DrugsEnterprise drugsEnterprise : drugsEnterprises) {
                            if ("aldyf".equals(drugsEnterprise.getCallSys()) || ("tmdyf".equals(drugsEnterprise.getCallSys()) && recipe.getPushFlag() == 1)) {
                                //向药企推送处方过期的通知
                                RemoteDrugEnterpriseService remoteDrugEnterpriseService = ApplicationUtils.getRecipeService(RemoteDrugEnterpriseService.class);
                                try {
                                    AccessDrugEnterpriseService remoteService = remoteDrugEnterpriseService.getServiceByDep(drugsEnterprise);
                                    DrugEnterpriseResult drugEnterpriseResult = remoteService.updatePrescriptionStatus(recipe.getRecipeCode(), AlDyfRecipeStatusConstant.EXPIRE);
                                    LOGGER.info("向药企推送处方过期通知,{}", JSONUtils.toString(drugEnterpriseResult));
                                } catch (Exception e) {
                                    LOGGER.info("向药企推送处方过期通知有问题{}", recipe.getRecipeId(), e);
                                }
                            }


                        }
                    }
                    memo.delete(0, memo.length());
                    int recipeId = recipe.getRecipeId();
                    //相应订单处理
                    order = orderDAO.getOrderByRecipeId(recipeId);
                    orderService.cancelOrder(order, OrderStatusConstant.CANCEL_AUTO, true);
                    if (recipe.getFromflag().equals(RecipeBussConstant.FROMFLAG_HIS_USE)) {
                        if(null != order){
                            orderDAO.updateByOrdeCode(order.getOrderCode(), ImmutableMap.of("cancelReason", "患者未在规定时间内支付，该处方单已失效"));
                        }
                        //发送超时取消消息
                        //${sendOrgan}：抱歉，您的处方单由于超过${overtime}未处理，处方单已失效。如有疑问，请联系开方医生或拨打${customerTel}联系小纳。
                        RecipeMsgService.sendRecipeMsg(RecipeMsgEnum.RECIPE_CANCEL_4HIS, recipe);
                    }

                    //变更处方状态
                    recipeDAO.updateRecipeInfoByRecipeId(recipeId, status, ImmutableMap.of("chooseFlag", 1));
                    RecipeMsgService.batchSendMsg(recipe, status);
                    if (RecipeBussConstant.RECIPEMODE_NGARIHEALTH.equals(recipe.getRecipeMode())) {
                        //药师首页待处理任务---取消未结束任务
                        ApplicationUtils.getBaseService(IAsynDoBussService.class).fireEvent(new BussCancelEvent(recipeId, BussTypeConstant.RECIPE));
                    }
                    if (RecipeStatusConstant.NO_PAY == status) {
                        memo.append("已取消,超过3天未支付");
                    } else if (RecipeStatusConstant.NO_OPERATOR == status) {
                        memo.append("已取消,超过3天未操作");
                    } else {
                        memo.append("未知状态:" + status);
                    }
                    if (RecipeStatusConstant.NO_PAY == status) {
                        //未支付，三天后自动取消后，优惠券自动释放
                        RecipeCouponService recipeCouponService = ApplicationUtils.getRecipeService(RecipeCouponService.class);
                        recipeCouponService.unuseCouponByRecipeId(recipeId);
                    }
                    //推送处方到监管平台
                    RecipeBusiThreadPool.submit(new PushRecipeToRegulationCallable(recipe.getRecipeId(), 1));
                    //HIS消息发送
                    boolean succFlag = hisService.recipeStatusUpdate(recipeId);
                    if (succFlag) {
                        memo.append(",HIS推送成功");
                    } else {
                        memo.append(",HIS推送失败");
                    }
                    //保存处方状态变更日志
                    RecipeLogService.saveRecipeLog(recipeId, RecipeStatusConstant.CHECK_PASS, status, memo.toString());
                }
                //修改cdr_his_recipe status为已处理
                orderService.updateHisRecieStatus(recipeList);
            }
        }

    }

    /**
     * 定时任务:处方单失效提醒
     * 根据处方单失效时间：
     * 如果医生签名确认时间是：9：00-24：00  ，在处方单失效前一天的晚上6点推送；
     * 如果医生签名确认时间是：00-8：59 ，在处方单失效前两天的晚上6点推送；
     */
    @RpcService
    public void remindRecipeTask() {
        RecipeDAO recipeDAO = getDAO(RecipeDAO.class);

        //处方失效前一天提醒，但是需要根据签名时间进行推送，故查数据时选择超过一天的数据就可以
        List<Integer> statusList = Arrays.asList(RecipeStatusConstant.PATIENT_NO_OPERATOR, RecipeStatusConstant.PATIENT_NO_PAY, RecipeStatusConstant.PATIENT_NODRUG_REMIND);
        Date now = DateTime.now().toDate();
        //设置查询时间段
        String endDt = DateConversion.getDateFormatter(DateConversion.getDateTimeDaysAgo(1), DateConversion.DEFAULT_DATE_TIME);
        String startDt = DateConversion.getDateFormatter(DateConversion.getDateTimeDaysAgo(Integer.parseInt(cacheService.getParam(ParameterConstant.KEY_RECIPE_VALIDDATE_DAYS, RECIPE_EXPIRED_DAYS.toString()))), DateConversion.DEFAULT_DATE_TIME);
        for (Integer status : statusList) {
            List<Recipe> recipeList = recipeDAO.getRecipeListForRemind(status, startDt, endDt);
            //筛选数据
            List<Integer> recipeIds = new ArrayList<>(10);
            for (Recipe recipe : recipeList) {
                Date signDate = recipe.getSignDate();
                if (null != signDate) {
                    int hour = DateConversion.getHour(signDate);
                    //签名时间在 00-8：59，则进行提醒
                    if (hour >= 0 && hour < 9) {
                        recipeIds.add(recipe.getRecipeId());
                    } else {
                        //如果是在9-24开的药，则判断签名时间与当前时间在2天后
                        int days = DateConversion.getDaysBetween(signDate, now);
                        if (days >= 2) {
                            recipeIds.add(recipe.getRecipeId());
                        }
                    }
                }
            }

            LOGGER.info("remindRecipeTask 状态=[{}], 提醒数量=[{}], 详情={}", status, recipeIds.size(), JSONUtils.toString(recipeIds));
            if (CollectionUtils.isNotEmpty(recipeIds)) {
                //批量更新 处方失效前提醒标志位
                recipeDAO.updateRemindFlagByRecipeId(recipeIds);
                //批量信息推送（失效前的消息提示取消）
                //RecipeMsgService.batchSendMsg(recipeIds, status);
            }
        }
    }

    /**
     * 定时任务: 查询过期的药师审核不通过，需要医生二次确认的处方
     * 查询规则: 药师审核不通过时间点的 2天前-1月前这段时间内，医生未处理的处方单
     * <p>
     * //date 2019/10/14
     * //修改规则：处方开放时间，时间点的 3天前-1月前这段时间内，医生未处理的处方单
     */
    @RpcService
    public void afterCheckNotPassYsTask() {
        RecipeDAO recipeDAO = getDAO(RecipeDAO.class);

        //将一次审方不通过的处方，搁置的设置成审核不通过
        String endDt = DateConversion.getDateFormatter(DateConversion.getDateTimeDaysAgo(3), DateConversion.DEFAULT_DATE_TIME);
        String startDt = DateConversion.getDateFormatter(DateConversion.getDateTimeDaysAgo(RECIPE_EXPIRED_SECTION), DateConversion.DEFAULT_DATE_TIME);
        //根据条件查询出来的数据都是需要主动退款的
        List<Recipe> list = recipeDAO.findFirstCheckNoPass(startDt, endDt);
        LOGGER.info("afterCheckNotPassYsTask 处理数量=[{}], 详情={}", list.size(), JSONUtils.toString(list));
        Map<String, Object> updateMap = new HashMap<>();
        for (Recipe recipe : list) {
            //判断处方是否有关联订单，
            if (null != recipe.getOrderCode()) {
                // 关联：修改处方一次审核不通过的标志位，并且把订单的状态审核成审核不通过
                //更新处方标志位
                updateMap.put("checkStatus", RecipecCheckStatusConstant.Check_Normal);
                recipeDAO.updateRecipeInfoByRecipeId(recipe.getRecipeId(), updateMap);
                //更新订单的状态，退款
                afterCheckNotPassYs(recipe);
            } else {
                // 不关联：修改处方一次审核不通过的标志位
                //更新处方标志位
                updateMap.put("checkStatus", RecipecCheckStatusConstant.Check_Normal);
                recipeDAO.updateRecipeInfoByRecipeId(recipe.getRecipeId(), updateMap);
            }
            //发消息(审核不通过的)
            //添加发送不通过消息
            RecipeMsgService.batchSendMsg(recipe, RecipeStatusConstant.CHECK_NOT_PASSYS_REACHPAY);
        }
    }

    /**
     * 定时任务:从HIS中获取处方单状态
     * 选择了到医院取药方法，需要定时从HIS上获取该处方状态数据
     */
    @RpcService
    public void getRecipeStatusFromHis() {
        RecipeDAO recipeDAO = getDAO(RecipeDAO.class);
        //设置查询时间段
        String startDt = DateConversion.getDateFormatter(DateConversion.getDateTimeDaysAgo(Integer.parseInt(cacheService.getParam(ParameterConstant.KEY_RECIPE_VALIDDATE_DAYS, RECIPE_EXPIRED_DAYS.toString()))), DateConversion.DEFAULT_DATE_TIME);
        String endDt = DateConversion.getDateFormatter(DateTime.now().toDate(), DateConversion.DEFAULT_DATE_TIME);
        //key为organId,value为recipdeCode集合
        Map<Integer, List<String>> map = Maps.newHashMap();
        List<Recipe> list = recipeDAO.getRecipeStatusFromHis(startDt, endDt);
        LOGGER.info("getRecipeStatusFromHis 需要同步HIS处方，数量=[{}]", (null == list) ? 0 : list.size());
        assembleQueryStatusFromHis(list, map);
        List<UpdateRecipeStatusFromHisCallable> callables = new ArrayList<>(0);
        for (Integer organId : map.keySet()) {
            callables.add(new UpdateRecipeStatusFromHisCallable(map.get(organId), organId));
        }
        if (CollectionUtils.isNotEmpty(callables)) {
            try {
                RecipeBusiThreadPool.submitList(callables);
            } catch (InterruptedException e) {
                LOGGER.error("getRecipeStatusFromHis 线程池异常");
            }
        }
    }

    /**
     * 自动审核通过情况
     *
     * @param result
     * @throws Exception
     */
    public void autoPassForCheckYs(CheckYsInfoBean result) throws Exception {
       /* Map<String, Object> checkParam = Maps.newHashMap();
        checkParam.put("recipeId", result.getRecipeId());
        checkParam.put("checkOrgan", result.getCheckOrganId());
        checkParam.put("checker", result.getCheckDoctorId());
        checkParam.put("result", 1);
        checkParam.put("failMemo", "");
        saveCheckResult(checkParam);*/
        //武昌项目用到--自动审核通过不走现在的审方逻辑
        RecipeDAO recipeDAO = getDAO(RecipeDAO.class);
        Recipe recipe = recipeDAO.getByRecipeId(result.getRecipeId());
        int recipeStatus = auditModeContext.getAuditModes(recipe.getReviewType()).afterAuditRecipeChange();
        recipeDAO.updateRecipeInfoByRecipeId(result.getRecipeId(), recipeStatus, null);
        LOGGER.info("autoPassForCheckYs recipeId={};status={}",result.getRecipeId(),recipeStatus);
        auditModeContext.getAuditModes(recipe.getReviewType()).afterCheckPassYs(recipe);
    }

    /**
     * 定时任务:更新药企token
     */
    @RpcService
    public void updateDrugsEnterpriseToken() {
        DrugsEnterpriseDAO drugsEnterpriseDAO = getDAO(DrugsEnterpriseDAO.class);
        List<Integer> list = drugsEnterpriseDAO.findNeedUpdateIds();
        LOGGER.info("updateDrugsEnterpriseToken 此次更新药企数量=[{}]", (null == list) ? 0 : list.size());
        RemoteDrugEnterpriseService remoteDrugService = ApplicationUtils.getRecipeService(RemoteDrugEnterpriseService.class);
        //非空已在方法内部判断
        remoteDrugService.updateAccessToken(list);
    }

    /**
     * 定时任务向患者推送确认收货微信消息
     */
    @RpcService
    public void pushPatientConfirmReceiptTask() {
        // 设置查询时间段
        String endDt = DateConversion.getDateFormatter(DateConversion.getDateTimeDaysAgo(3), DateConversion.DEFAULT_DATE_TIME);
        String startDt = DateConversion.getDateFormatter(DateConversion.getDateTimeDaysAgo(RECIPE_EXPIRED_SECTION), DateConversion.DEFAULT_DATE_TIME);

        RecipeDAO recipeDAO = getDAO(RecipeDAO.class);
        List<Recipe> recipes = recipeDAO.findNotConfirmReceiptList(startDt, endDt);

        LOGGER.info("pushPatientConfirmReceiptTask size={}, detail={}", recipes.size(), JSONUtils.toString(recipes));
        // 批量信息推送
        RecipeMsgService.batchSendMsgForNew(recipes, RecipeStatusConstant.RECIPR_NOT_CONFIRM_RECEIPT);
    }

    /************************************************患者类接口 START*************************************************/

    /**
     * 健康端获取处方详情
     *
     * @param recipeId 处方ID
     * @return HashMap<String, Object>
     */
    @RpcService
    public Map<String, Object> getPatientRecipeById(int recipeId) {
        checkUserHasPermission(recipeId);

        Map<String, Object> result = RecipeServiceSub.getRecipeAndDetailByIdImpl(recipeId, false);
        PatientDTO patient = (PatientDTO) result.get("patient");
        result.put("patient", ObjectCopyUtils.convert(patient, PatientDS.class));
        return result;
    }

    /**
     * 健康端获取处方详情-----合并处方
     * @param ext 没用
     * @param recipeIds 处方ID列表
     */
    @RpcService
    public List<Map<String, Object>> findPatientRecipesByIds(Integer ext, List<Integer> recipeIds) {
        //把处方对象返回给前端--合并处方--原确认订单页面的处方详情是通过getPatientRecipeById获取的
        if (CollectionUtils.isNotEmpty(recipeIds)) {
            List<Map<String, Object>> recipeInfos = new ArrayList<>(recipeIds.size());
            for (Integer recipeId : recipeIds) {
                recipeInfos.add(RecipeServiceSub.getRecipeAndDetailByIdImpl(recipeId, false));
            }
            return recipeInfos;
        }
        return null;
    }

    /**
     * 处方签获取
     *
     * @param recipeId
     * @param organId
     * @return
     */
    @RpcService
    public Map<String, List<RecipeLabelVO>> queryRecipeLabelById(int recipeId, Integer organId) {
        Map<String, Object> recipeMap = RecipeServiceSub.getRecipeAndDetailByIdImpl(recipeId, false);
        if (org.springframework.util.CollectionUtils.isEmpty(recipeMap)) {
            throw new DAOException(recipe.constant.ErrorCode.SERVICE_ERROR, "recipe is null!");
        }
        Map<String, List<RecipeLabelVO>> result = recipeLabelManager.queryRecipeLabelById(organId, recipeMap);
        return result;
    }

    /**
     * 获取该处方的购药方式(用于判断这个处方是不是被处理)
     *
     * @param recipeId
     * @param flag     1:表示处方单详情页从到院取药转直接支付的情况判断
     * @return 0未处理  1线上支付 2货到付款 3到院支付
     */


    @RpcService
    public int getRecipePayMode(int recipeId, int flag) {
        RecipeDAO recipeDAO = getDAO(RecipeDAO.class);
        Recipe dbRecipe = recipeDAO.getByRecipeId(recipeId);
        if (null == dbRecipe) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "recipe not exist!");
        }

        //进行判断该处方单是否已处理，已处理则返回具体购药方式
        if (1 == dbRecipe.getChooseFlag()) {
            //如果之前选择的是到院取药且未支付 则可以进行转在线支付的方式
            if (1 == flag && RecipeBussConstant.GIVEMODE_TO_HOS.equals(dbRecipe.getGiveMode()) && RecipeBussConstant.GIVEMODE_TFDS.equals(dbRecipe.getPayMode()) && 0 == dbRecipe.getPayFlag()) {
                return 0;
            }
            return dbRecipe.getPayMode();
        } else {
            return 0;
        }

    }

    /**
     * 判断该处方是否支持医院取药
     *
     * @param clinicOrgan 开药机构
     * @return boolean
     */
    @Deprecated
    @RpcService
    public boolean supportTakeMedicine(Integer recipeId, Integer clinicOrgan) {
        if (null == recipeId) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "recipeId is required!");
        }

        if (null == clinicOrgan) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "clinicOrgan is required!");
        }
        boolean succFlag = false;
        //date 20191022到院取药取配置项
        boolean flag = RecipeServiceSub.getDrugToHos(recipeId, clinicOrgan);
        //是否支持医院取药 true：支持
        if (flag) {
            String backInfo = searchRecipeStatusFromHis(recipeId, 1);
            if (StringUtils.isNotEmpty(backInfo)) {
                succFlag = false;
                throw new DAOException(ErrorCode.SERVICE_ERROR, backInfo);
            }
        } else {
            LOGGER.error("supportTakeMedicine organ[" + clinicOrgan + "] not support take medicine!");
        }

        return succFlag;
    }

    /**
     * 扩展配送校验方法
     *
     * @param recipeId
     * @param clinicOrgan
     * @param selectDepId 可能之前选定了某个药企
     * @param payMode
     * @return
     */
    public Integer supportDistributionExt(Integer recipeId, Integer clinicOrgan, Integer selectDepId, Integer payMode) {
        Integer backDepId = null;
        //date 20191022 修改到院取药配置项
        boolean flag = RecipeServiceSub.getDrugToHos(recipeId, clinicOrgan);
        //是否支持医院取药 true：支持
        //该医院不对接HIS的话，则不需要进行该校验
        if (flag) {
            String backInfo = searchRecipeStatusFromHis(recipeId, 2);
            if (StringUtils.isNotEmpty(backInfo)) {
                throw new DAOException(ErrorCode.SERVICE_ERROR, backInfo);
            }
        }

        //进行药企配送分配，检测药企是否有能力进行该处方的配送
        Integer depId = getDrugsEpsIdByOrganId(recipeId, payMode, selectDepId);
        if (!Integer.valueOf(-1).equals(depId)) {
            if (!(null != selectDepId && !selectDepId.equals(depId))) {
                //不是同一家药企配送，无法配送
                backDepId = depId;
            }
        }

        return backDepId;
    }

    private String getWxAppIdForRecipeFromOps(Integer recipeId, Integer busOrgan) {
        IPaymentService iPaymentService = ApplicationUtils.getBaseService(IPaymentService.class);
        //参数二 PayWayEnum.WEIXIN_WAP
        //参数三 BusTypeEnum.RECIPE
        return iPaymentService.getPayAppId(busOrgan, "40", BusTypeEnum.RECIPE.getCode());
    }

    /**
     * 根据开方机构分配药企进行配送并入库 （获取某一购药方式最合适的供应商）
     *
     * @param recipeId
     * @param payMode
     * @param selectDepId
     * @return
     */
    public Integer getDrugsEpsIdByOrganId(Integer recipeId, Integer payMode, Integer selectDepId) {
        RecipeDAO recipeDAO = getDAO(RecipeDAO.class);
        Recipe recipe = recipeDAO.getByRecipeId(recipeId);
        Integer depId = -1;
        if (null != recipe) {
            List<DrugsEnterprise> list = findSupportDepList(Arrays.asList(recipeId), recipe.getClinicOrgan(), payMode, true, selectDepId);
            if (CollectionUtils.isNotEmpty(list)) {
                depId = list.get(0).getId();
            }
        } else {
            LOGGER.error("getDrugsEpsIdByOrganId 处方[" + recipeId + "]不存在！");
        }

        return depId;
    }

    /**
     * 查询符合条件的药企供应商
     *
     * @param recipeIdList 处方ID
     * @param organId      开方机构
     * @param payMode      购药方式，为NULL时表示查询所有药企
     * @param sigle        true:表示只返回第一个合适的药企，false:表示符合条件的所有药企
     * @param selectDepId  指定某个药企
     * @return
     */
    public List<DrugsEnterprise> findSupportDepList(List<Integer> recipeIdList, int organId, Integer payMode, boolean sigle, Integer selectDepId) {
        DrugsEnterpriseDAO drugsEnterpriseDAO = getDAO(DrugsEnterpriseDAO.class);
        SaleDrugListDAO saleDrugListDAO = getDAO(SaleDrugListDAO.class);
        RecipeDetailDAO recipeDetailDAO = getDAO(RecipeDetailDAO.class);
        RemoteDrugEnterpriseService remoteDrugService = ApplicationUtils.getRecipeService(RemoteDrugEnterpriseService.class);
        IHisConfigService iHisConfigService = ApplicationUtils.getBaseService(IHisConfigService.class);

        List<DrugsEnterprise> backList = new ArrayList<>(5);
        //线上支付能力判断
        boolean onlinePay = true;
        if (null == payMode || RecipeBussConstant.PAYMODE_ONLINE.equals(payMode) || RecipeBussConstant.PAYMODE_MEDICAL_INSURANCE.equals(payMode)) {
            //需要判断医院HIS是否开通
            boolean hisStatus = iHisConfigService.isHisEnable(organId);
            LOGGER.info("findSupportDepList payAccount={}, hisStatus={}", null, hisStatus);
            if (!hisStatus) {
                LOGGER.error("findSupportDepList 机构[" + organId + "]不支持线上支付！");
                //这里判断payMode=null的情况，是为了筛选供应商提供依据
                if (null == payMode) {
                    onlinePay = false;
                } else {
                    return backList;
                }
            }
        }

        for (Integer recipeId : recipeIdList) {
            List<DrugsEnterprise> subDepList = new ArrayList<>(5);
            //检测配送的药品是否按照完整的包装开的药，如 1*20支，开了10支，则不进行选择，数据库里主要是useTotalDose不为小数
            List<Double> totalDoses = recipeDetailDAO.findUseTotalDoseByRecipeId(recipeId);
            if (null != totalDoses && !totalDoses.isEmpty()) {
                for (Double totalDose : totalDoses) {
                    if (null != totalDose) {
                        int itotalDose = (int) totalDose.doubleValue();
                        if (itotalDose != totalDose.doubleValue()) {
                            LOGGER.error("findSupportDepList 不支持非完整包装的计量药品配送. recipeId=[{}], totalDose=[{}]", recipeId, totalDose);
                            break;
                        }
                    } else {
                        LOGGER.error("findSupportDepList 药品计量为null. recipeId=[{}]", recipeId);
                        break;
                    }
                }
            } else {
                LOGGER.error("findSupportDepList 所有药品计量为null. recipeId=[{}]", recipeId);
                break;
            }

            List<Integer> drugIds = recipeDetailDAO.findDrugIdByRecipeId(recipeId);
            if (CollectionUtils.isEmpty(drugIds)) {
                LOGGER.error("findSupportDepList 处方[{}]没有任何药品！", recipeId);
                break;
            }

            List<DrugsEnterprise> drugsEnterpriseList = new ArrayList<>(0);
            if (null != selectDepId) {
                DrugsEnterprise drugsEnterprise = drugsEnterpriseDAO.getById(selectDepId);
                if (null != drugsEnterprise) {
                    drugsEnterpriseList.add(drugsEnterprise);
                }
            } else {
                if (null != payMode) {
                    List<Integer> payModeSupport = RecipeServiceSub.getDepSupportMode(payMode);
                    if (CollectionUtils.isEmpty(payModeSupport)) {
                        LOGGER.error("findSupportDepList 处方[{}]无法匹配配送方式. payMode=[{}]", recipeId, payMode);
                        break;
                    }

                    //筛选出来的数据已经去掉不支持任何方式配送的药企
                    drugsEnterpriseList = drugsEnterpriseDAO.findByOrganIdAndPayModeSupport(organId, payModeSupport);
                    if (CollectionUtils.isEmpty(drugsEnterpriseList)) {
                        LOGGER.error("findSupportDepList 处方[{}]没有任何药企可以进行配送！", recipeId);
                        break;
                    }
                } else {
                    drugsEnterpriseList = drugsEnterpriseDAO.findByOrganId(organId);
                }
            }

            for (DrugsEnterprise dep : drugsEnterpriseList) {
                //根据药企是否能满足所有配送的药品优先
                Integer depId = dep.getId();
                //不支持在线支付跳过该药企
                if (Integer.valueOf(1).equals(dep.getPayModeSupport()) && !onlinePay) {
                    continue;
                }
                //药品匹配成功标识
                boolean succFlag = false;
                //date 20200921 修改【his管理的药企】不用校验配送药品，由预校验结果
                if(new Integer(1).equals(RecipeServiceSub.getOrganEnterprisesDockType(organId))){
                    succFlag = true;
                }else{
                    Long count = saleDrugListDAO.getCountByOrganIdAndDrugIds(depId, drugIds);
                    if (null != count && count > 0) {
                        if (count == drugIds.size()) {
                            succFlag = true;
                        }
                    }
                }

                if (!succFlag) {
                    LOGGER.error("findSupportDepList 药企名称=[{}]存在不支持配送药品. 处方ID=[{}], 药企ID=[{}], drugIds={}", dep.getName(), recipeId, depId, JSONUtils.toString(drugIds));
                    continue;
                } else {
                    //通过查询该药企库存，最终确定能否配送
                    //todo--返回具体的没库存的药--新写个接口
                    DrugEnterpriseResult result = remoteDrugService.scanStock(recipeId, dep);
                    succFlag = result.getCode().equals(DrugEnterpriseResult.SUCCESS) ? true : false;
                    if (succFlag || dep.getCheckInventoryFlag() == 2) {
                        subDepList.add(dep);
                        //只需要查询单供应商就返回
                        if (sigle) {
                            break;
                        }
                        LOGGER.info("findSupportDepList 药企名称=[{}]支持配送该处方所有药品. 处方ID=[{}], 药企ID=[{}], drugIds={}", dep.getName(), recipeId, depId, JSONUtils.toString(drugIds));
                    } else {
                        LOGGER.error("findSupportDepList  药企名称=[{}]药企库存查询返回药品无库存. 处方ID=[{}], 药企ID=[{}]", dep.getName(), recipeId, depId);
                    }
                }
            }

            if (CollectionUtils.isEmpty(subDepList)) {
                LOGGER.error("findSupportDepList 该处方获取不到支持的药企无法配送. recipeId=[{}]", recipeId);
                backList.clear();
                break;
            } else {
                //药企求一个交集
                if (CollectionUtils.isEmpty(backList)) {
                    backList.addAll(subDepList);
                } else {
                    //交集需要处理
                    backList.retainAll(subDepList);
                }
            }
        }

        return backList;
    }

    /**
     * 手动进行处方退款服务
     *
     * @param recipeId
     * @param operName
     * @param reason
     */
    @RpcService
    public void manualRefundForRecipe(int recipeId, String operName, String reason) {
        wxPayRefundForRecipe(4, recipeId, "操作人:[" + ((StringUtils.isEmpty(operName)) ? "" : operName) + "],理由:[" + ((StringUtils.isEmpty(reason)) ? "" : reason) + "]");
    }

    /**
     * 患者手动退款
     *
     * @return
     **/
    @RpcService
    public void patientRefundForRecipe(int recipeId) {
        wxPayRefundForRecipe(5, recipeId, "患者手动申请退款");

        RecipeDAO recipeDAO = getDAO(RecipeDAO.class);
        Recipe recipe = recipeDAO.getByRecipeId(recipeId);

        CommonResponse response = null;
        HisSyncSupervisionService hisSyncService = ApplicationUtils.getRecipeService(HisSyncSupervisionService.class);
        try {
            response = hisSyncService.uploadRecipeVerificationIndicators(Arrays.asList(recipe));
            if (CommonConstant.SUCCESS.equals(response.getCode())) {
                //记录日志
                RecipeLogService.saveRecipeLog(recipe.getRecipeId(), recipe.getStatus(),
                        recipe.getStatus(), "监管平台上传处方退款信息成功");
                LOGGER.info("patientRefundForRecipe execute success. recipeId={}", recipe.getRecipeId());
            } else {
                RecipeLogService.saveRecipeLog(recipe.getRecipeId(), recipe.getStatus(),
                        recipe.getStatus(), "监管平台上传处方退款信息失败," + response.getMsg());
                LOGGER.warn("patientRefundForRecipe execute error. recipe={}", JSONUtils.toString(recipe));
            }
        } catch (Exception e) {
            LOGGER.warn("patientRefundForRecipe exception recipe={}", JSONUtils.toString(recipe), e);
        }
    }


    /**
     * 退款方法
     *
     * @param flag
     * @param recipeId
     */
    @RpcService
    public void wxPayRefundForRecipe(int flag, int recipeId, String log) {
        RecipeDAO recipeDAO = getDAO(RecipeDAO.class);
        RecipeOrderService orderService = ApplicationUtils.getRecipeService(RecipeOrderService.class);

        Recipe recipe = recipeDAO.getByRecipeId(recipeId);
        int status = recipe.getStatus();

        String errorInfo = "退款-";
        switch (flag) {
            case 1:
                errorInfo += "HIS线上支付返回：写入his失败";
                RecipeMsgService.batchSendMsg(recipe, RecipeStatusConstant.PATIENT_HIS_FAIL);
                break;
            case 2:
                errorInfo += "药师审核不通过";
                break;
            case 3:
                errorInfo += "推送药企失败";
                RecipeMsgService.batchSendMsg(recipe, RecipeStatusConstant.RECIPE_LOW_STOCKS);
                break;
            case 4:
                errorInfo += log;
                status = RecipeStatusConstant.REVOKE;
                break;
            case 5:
                errorInfo += log;
                status = RecipeStatusConstant.REVOKE;
                break;
            case 6:
                errorInfo += log;
                break;
            default:
                errorInfo += "未知,flag=" + flag;

        }

        RecipeLogService.saveRecipeLog(recipeId, recipe.getStatus(), status, errorInfo);

        //相应订单处理
        RecipeOrderDAO orderDAO = getDAO(RecipeOrderDAO.class);
        RecipeOrder order = orderDAO.getByOrderCode(recipe.getOrderCode());
        List<Integer> recipeIds = JSONUtils.parse(order.getRecipeIdList(), List.class);
        if (1 == flag || 6 == flag) {
            orderService.updateOrderInfo(order.getOrderCode(), ImmutableMap.of("status", OrderStatusConstant.READY_PAY), null);
        } else if (PUSH_FAIL == flag) {
            orderService.cancelOrder(order, OrderStatusConstant.CANCEL_AUTO, false);
        } else if (REFUND_MANUALLY == flag) {
            orderService.cancelOrder(order, OrderStatusConstant.CANCEL_AUTO, false);
            for (Integer recid : recipeIds) {
                //处理处方单
                recipeDAO.updateRecipeInfoByRecipeId(recid, status, null);
            }
        } else if (REFUND_PATIENT == flag) {
            orderService.cancelOrder(order, OrderStatusConstant.CANCEL_AUTO, false);
            orderService.updateOrderInfo(order.getOrderCode(), ImmutableMap.of("payFlag", 2), null);
            for (Integer recid : recipeIds) {
                //处理处方单
                recipeDAO.updateRecipeInfoByRecipeId(recid, status, null);
            }
        }
        orderService.updateOrderInfo(order.getOrderCode(), ImmutableMap.of("refundFlag", 1, "refundTime", new Date()), null);

        try {
            //退款
            INgariRefundService rufundService = BaseAPI.getService(INgariRefundService.class);
            rufundService.refund(order.getOrderId(), RecipeService.WX_RECIPE_BUSTYPE);
        } catch (Exception e) {
            LOGGER.error("wxPayRefundForRecipe " + errorInfo + "*****微信退款异常！recipeId[" + recipeId + "],err[" + e.getMessage() + "]", e);
        }

        try {
            if (CHECK_NOT_PASS == flag || PUSH_FAIL == flag || REFUND_MANUALLY == flag) {
                //HIS消息发送
                RecipeHisService hisService = ApplicationUtils.getRecipeService(RecipeHisService.class);
                hisService.recipeRefund(recipeId);
            }
        } catch (Exception e) {
            LOGGER.error("wxPayRefundForRecipe " + errorInfo + "*****HIS消息发送异常！recipeId[" + recipeId + "],err[" + e.getMessage() + "]", e);
        }

    }

    /************************************************患者类接口 END***************************************************/


    /**
     * 组装从HIS获取处方状态的map，key为organId,value为HIS端处方编号 recipeCode集合
     *
     * @param list
     * @param map
     */
    private void assembleQueryStatusFromHis(List<Recipe> list, Map<Integer, List<String>> map) {
        if (CollectionUtils.isNotEmpty(list)) {
            for (Recipe recipe : list) {
                //到院取药的去查询HIS状态
                if (RecipeStatusConstant.HAVE_PAY == recipe.getStatus() || RecipeStatusConstant.CHECK_PASS == recipe.getStatus()) {
                    if (!map.containsKey(recipe.getClinicOrgan())) {
                        map.put(recipe.getClinicOrgan(), new ArrayList<String>(0));
                    }

                    if (StringUtils.isNotEmpty(recipe.getRecipeCode())) {
                        map.get(recipe.getClinicOrgan()).add(recipe.getRecipeCode());
                    }
                }
            }
        }
    }

    /**
     * 获取当前患者所有家庭成员(包括自己)
     *
     * @param mpiId
     * @return
     */
    public List<String> getAllMemberPatientsByCurrentPatient(String mpiId) {
        List<String> allMpiIds = Lists.newArrayList();
        String loginId = patientService.getLoginIdByMpiId(mpiId);
        if (StringUtils.isNotEmpty(loginId)) {
            allMpiIds = patientService.findMpiIdsByLoginId(loginId);
        }
        return allMpiIds;
        /*//获取所有家庭成员的患者编号
        List<String> allMpiIds = iPatientService.findMemberMpiByMpiid(mpiId);
        if (null == allMpiIds) {
            allMpiIds = new ArrayList<>(0);
        }
        //加入患者自己的编号
        allMpiIds.add(mpiId);
        return allMpiIds;*/
    }

    /**
     * 在线续方首页，获取当前登录患者待处理处方单
     *
     * @param mpiid 当前登录患者mpiid
     * @return
     */
    @RpcService
    public RecipeResultBean getHomePageTaskForPatient(String mpiid) {
        LOGGER.info("getHomePageTaskForPatient mpiId={}", mpiid);
        RecipeDAO recipeDAO = getDAO(RecipeDAO.class);
        //根据mpiid获取当前患者所有家庭成员(包括自己)
        List<String> allMpiIds = getAllMemberPatientsByCurrentPatient(mpiid);
        //获取患者待处理处方单id
        List<Integer> recipeIds = recipeDAO.findPendingRecipes(allMpiIds, RecipeStatusConstant.CHECK_PASS, 0, Integer.MAX_VALUE);
        //获取患者历史处方单，有一个即不为空
        List<PatientRecipeBean> backList = recipeDAO.findOtherRecipesForPatient(allMpiIds, recipeIds, 0, 1);
        RecipeResultBean resultBean = RecipeResultBean.getSuccess();

        if (CollectionUtils.isEmpty(recipeIds)) {
            if (CollectionUtils.isEmpty(backList)) {
                resultBean.setExtendValue("-1");
                resultBean.setMsg("查看我的处方单");
            } else {
                resultBean.setExtendValue("0");
                resultBean.setMsg("查看我的处方单");
            }
        } else {
            resultBean.setExtendValue("1");
            resultBean.setMsg(String.valueOf(recipeIds.size()));
        }
        return resultBean;
    }

    /**
     * 处方订单下单时和下单之后对处方单的更新
     *
     * @param saveFlag
     * @param recipeId
     * @param payFlag
     * @param info
     * @return
     */
    public RecipeResultBean updateRecipePayResultImplForOrder(boolean saveFlag, Integer recipeId, Integer payFlag, Map<String, Object> info, BigDecimal recipeFee) {
        RecipeResultBean result = RecipeResultBean.getSuccess();
        if (null == recipeId) {
            result.setCode(RecipeResultBean.FAIL);
            result.setError("处方单id为null");
            return result;
        }

        RecipeDAO recipeDAO = getDAO(RecipeDAO.class);

        Map<String, Object> attrMap = Maps.newHashMap();
        if (null != info) {
            attrMap.putAll(info);
        }
        Integer payMode = MapValueUtil.getInteger(attrMap, "payMode");
        Integer giveMode = null;
        if (RecipeBussConstant.PAYMODE_TFDS.equals(payMode)) {
            //到店取药
            giveMode = RecipeBussConstant.GIVEMODE_TFDS;
        } else if (RecipeBussConstant.PAYMODE_COD.equals(payMode) || RecipeBussConstant.PAYMODE_ONLINE.equals(payMode) || RecipeBussConstant.PAYMODE_MEDICAL_INSURANCE.equals(payMode)) {
            //配送到家
            giveMode = RecipeBussConstant.GIVEMODE_SEND_TO_HOME;
        } else if (RecipeBussConstant.PAYMODE_TO_HOS.equals(payMode)) {
            //到院取药
            giveMode = RecipeBussConstant.GIVEMODE_TO_HOS;
        } else if (RecipeBussConstant.PAYMODE_DOWNLOAD_RECIPE.equals(payMode)) {
            giveMode = RecipeBussConstant.GIVEMODE_DOWNLOAD_RECIPE;
        } else {
            giveMode = null;
        }
        attrMap.put("giveMode", giveMode);
        Recipe dbRecipe = recipeDAO.getByRecipeId(recipeId);

        if (saveFlag && RecipeResultBean.SUCCESS.equals(result.getCode())) {
            if (RecipeBussConstant.FROMFLAG_PLATFORM.equals(dbRecipe.getFromflag()) || RecipeBussConstant.FROMFLAG_HIS_USE.equals(dbRecipe.getFromflag())) {
                //异步显示对应的药品金额，
                RecipeBusiThreadPool.execute(new UpdateTotalRecipePdfRunable(recipeId, recipeFee));
                //HIS消息发送
                RecipeHisService hisService = ApplicationUtils.getRecipeService(RecipeHisService.class);
                hisService.recipeDrugTake(recipeId, payFlag, result);
            }
        }
        if (RecipeResultBean.SUCCESS.equals(result.getCode())) {
            //根据审方模式改变
            auditModeContext.getAuditModes(dbRecipe.getReviewType()).afterPayChange(saveFlag, dbRecipe, result, attrMap);
            //支付成功后pdf异步显示对应的配送信息
            if(new Integer("1").equals(payFlag)){
                RecipeBusiThreadPool.execute(new UpdateReceiverInfoRecipePdfRunable(recipeId));
            }
        }
        return result;
    }


    /**
     * 查询单个处方在HIS中的状态
     *
     * @param recipeId
     * @param modelFlag
     * @return
     */
    public String searchRecipeStatusFromHis(Integer recipeId, int modelFlag) {
        LOGGER.info("searchRecipeStatusFromHis " + ((1 == modelFlag) ? "supportTakeMedicine" : "supportDistribution") + "  recipeId=" + recipeId);
        RecipeHisService hisService = ApplicationUtils.getRecipeService(RecipeHisService.class);
        //HIS发送消息
        return hisService.recipeSingleQuery(recipeId);
    }

    /**
     * 患者mpiId变更后修改处方数据内容
     *
     * @param newMpiId
     * @param oldMpiId
     */
    public void updatePatientInfoForRecipe(String newMpiId, String oldMpiId) {
        if (StringUtils.isNotEmpty(newMpiId) && StringUtils.isNotEmpty(oldMpiId)) {
            RecipeDAO recipeDAO = getDAO(RecipeDAO.class);
            Integer count = recipeDAO.updatePatientInfoForRecipe(newMpiId, oldMpiId);
            LOGGER.info("updatePatientInfoForRecipe newMpiId=[{}], oldMpiId=[{}], count=[{}]", newMpiId, oldMpiId, count);
        }
    }

    @RpcService
    public Map<String, Object> getHosRecipeList(Integer consultId, Integer organId, String mpiId) {
        RecipePreserveService preserveService = ApplicationUtils.getRecipeService(RecipePreserveService.class);
        //查询3个月以前的历史处方数据
        return preserveService.getHosRecipeList(consultId, organId, mpiId, 180);
    }

    @RpcService
    public RecipeResultBean getPageDetail(int recipeId) {
        RecipeResultBean result = RecipeResultBean.getSuccess();
        Recipe nowRecipe = DAOFactory.getDAO(RecipeDAO.class).get(recipeId);
        if (null == nowRecipe) {
            LOGGER.info("getPageDetailed: [recipeId:" + recipeId + "] 对应的处方信息不存在！");
            result.setCode(RecipeResultBean.FAIL);
            result.setError("处方单id对应的处方为空");
            return result;
        }
        Map<String, Object> ext = new HashMap<>(10);
        Map<String, Object> recipeMap = getPatientRecipeById(recipeId);
        if (null == nowRecipe.getOrderCode()) {
            result.setObject(recipeMap);
            ext.put("jumpType", "0");
            result.setExt(ext);
        } else {
            RecipeOrderService orderService = ApplicationUtils.getRecipeService(RecipeOrderService.class);
            RecipeResultBean orderDetail = orderService.getOrderDetail(nowRecipe.getOrderCode());
            result = orderDetail;
            Map<String, Object> nowExt = result.getExt();
            if (null == nowExt) {
                ext.put("jumpType", "1");
                result.setExt(ext);

            } else {
                nowExt.put("jumpType", "1");
                result.setExt(nowExt);
            }
            //date 2019/10/18
            //添加逻辑：添加处方的信息
            if (null != result.getObject()) {
                RecipeOrderBean orderBean = (RecipeOrderBean) result.getObject();
                RecipeOrderInfoBean infoBean = ObjectCopyUtils.convert(orderBean, RecipeOrderInfoBean.class);
                infoBean.setRecipeInfoMap(recipeMap);
                result.setObject(infoBean);
            }

        }
        return result;
    }

    @RpcService
    public RecipeResultBean changeRecipeStatusInfo(int recipeId, int status) {
        RecipeResultBean result = RecipeResultBean.getSuccess();
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        Recipe recipe = recipeDAO.get(recipeId);
        if (null == recipe) {
            LOGGER.info("changeRecipeStatusInfo: [recipeId:" + recipeId + "] 对应的处方信息不存在！");
            result.setCode(RecipeResultBean.FAIL);
            result.setError("处方单id对应的处方为空");
            return result;
        }
        Map<String, Object> searchMap = new HashMap<>(10);
        //判断修改的处方的状态是否是已下载
        if (status == RecipeStatusConstant.RECIPE_DOWNLOADED) {
            //当前处方下载处方状态的时候，确认处方的购药方式
            //首先判断处方的
            if (havChooseFlag == recipe.getChooseFlag() && RecipeBussConstant.GIVEMODE_DOWNLOAD_RECIPE != recipe.getGiveMode()) {
                LOGGER.info("changeRecipeStatusInfo: [recipeId:" + recipeId + "] 对应的处方的购药方式不是下载处方不能设置成已下载状态！");
                result.setCode(RecipeResultBean.FAIL);
                result.setError("处方选择的购药方式不是下载处方");
                return result;
            }
            Integer beforStatus = recipe.getStatus();
            if (beforStatus == RecipeStatusConstant.REVOKE) {
                throw new DAOException(eh.base.constant.ErrorCode.SERVICE_ERROR, "处方单已被撤销");
            }
            searchMap.put("giveMode", RecipeBussConstant.GIVEMODE_DOWNLOAD_RECIPE);
            searchMap.put("chooseFlag", havChooseFlag);
            //更新处方的信息
            Boolean updateResult = recipeDAO.updateRecipeInfoByRecipeId(recipeId, RecipeStatusConstant.RECIPE_DOWNLOADED, searchMap);
            //更新处方log信息
            if (updateResult) {
                RecipeLogService.saveRecipeLog(recipeId, beforStatus, RecipeStatusConstant.RECIPE_DOWNLOADED, "已下载状态修改成功");
            } else {
                LOGGER.info("changeRecipeStatusInfo: [recipeId:" + recipeId + "] 处方更新已下载状态失败！");
                result.setCode(RecipeResultBean.FAIL);
                result.setError("处方更新已下载状态失败");
                return result;
            }
            //处方来源于线下转线上的处方单
            if (recipe.getRecipeSourceType() == 2) {
                HisRecipeDAO hisRecipeDAO = getDAO(HisRecipeDAO.class);
                HisRecipe hisRecipe = hisRecipeDAO.getHisRecipeByRecipeCodeAndClinicOrgan(recipe.getClinicOrgan(), recipe.getRecipeCode());
                if (hisRecipe != null) {
                    hisRecipeDAO.updateHisRecieStatus(recipe.getClinicOrgan(), recipe.getRecipeCode(), 2);
                }
            }
        }
        return result;
    }

    /**
     * 定时任务:定时将下载处方后3天的处方设置成已完成
     * 每小时扫描一次，当前时间到前3天时间轴上的处方已下载
     */
    @RpcService
    public void changeDownLoadToFinishTask() {
        LOGGER.info("changeDownLoadToFinishTask: 开始定时任务，设置已下载3天后处方为已完成！");
        //首先获取当前时间前6天的时间到当前时间前3天时间区间
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern(DateConversion.DEFAULT_DATE_TIME);
        String endDate = LocalDateTime.now().minusDays(3).format(fmt);
        String startDate = LocalDateTime.now().minusDays(6).format(fmt);
        //获取当前时间区间状态是已下载的处方单
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        List<Recipe> recipeList = recipeDAO.findDowloadedRecipeToFinishList(startDate, endDate);
        Integer recipeId;
        //将处方单状态设置为已完成
        if (CollectionUtils.isNotEmpty(recipeList)) {
            for (Recipe recipe : recipeList) {
                //更新处方的状态-已完成
                recipeId = recipe.getRecipeId();
                Boolean rs = recipeDAO.updateRecipeInfoByRecipeId(recipe.getRecipeId(), RecipeStatusConstant.FINISH, null);
                //完成订单
                if (rs) {
                    LOGGER.info("changeDownLoadToFinishTask: 处方{}设置处方为已完成！", recipeId);
                    //完成订单
                    RecipeOrderService orderService = ApplicationUtils.getRecipeService(RecipeOrderService.class);
                    RecipeHisService hisService = ApplicationUtils.getRecipeService(RecipeHisService.class);

                    orderService.finishOrder(recipe.getOrderCode(), recipe.getPayMode(), null);
                    LOGGER.info("changeDownLoadToFinishTask: 订单{}设置为已完成！", recipe.getOrderCode());
                    //记录日志
                    RecipeLogService.saveRecipeLog(recipeId, RecipeStatusConstant.RECIPE_DOWNLOADED, RecipeStatusConstant.FINISH, "下载处方订单完成");
                    //HIS消息发送
                    hisService.recipeFinish(recipeId);
                    //发送取药完成消息(暂时不需要发送消息推送)

                    //监管平台核销上传
                    SyncExecutorService syncExecutorService = ApplicationUtils.getRecipeService(SyncExecutorService.class);
                    syncExecutorService.uploadRecipeVerificationIndicators(recipeId);
                } else {
                    LOGGER.warn("处方：{},更新失败", recipe.getRecipeId());
                }
            }
        }

    }


    /**
     * 过期废弃
     */
    @RpcService
    @Deprecated
    public void updateHisDrug(DrugInfoTO drug) {
        //校验药品数据安全
        if (!checkDrugInfo(drug)) {
            return;
        }
        LOGGER.info("updateHisDrug organId=[{}],当前同步药品数据:{}.", drug.getOrganId(), JSONUtils.toString(drug));

        Integer oid = drug.getOrganId();
        OrganDrugListDAO organDrugListDAO = getDAO(OrganDrugListDAO.class);

        OrganDrugList organDrug = organDrugListDAO.getByOrganIdAndOrganDrugCode(oid, drug.getDrcode());
        LOGGER.info("updateHisDrug 更新药品金额,更新前药品信息：{}", JSONUtils.toString(organDrug));
        updateHisDrug(drug, organDrug);
    }

    /**
     * 当前同步药品数据
     *
     * @param drug
     * @param organDrug
     */
    private void updateHisDrug(DrugInfoTO drug, OrganDrugList organDrug) {
        if (null == organDrug) {
            return;
        }
        //获取金额
        if (StringUtils.isNotEmpty(drug.getDrugPrice())) {
            BigDecimal drugPrice = new BigDecimal(drug.getDrugPrice());
            organDrug.setSalePrice(drugPrice);
        }
        if (StringUtils.isNotEmpty(drug.getDrmodel())) {
            organDrug.setDrugSpec(drug.getDrmodel());
        }
        if (StringUtils.isNotEmpty(drug.getMedicalDrugCode())) {
            organDrug.setMedicalDrugCode(drug.getMedicalDrugCode());
        }
        if (StringUtils.isNotEmpty(drug.getPack())) {
            organDrug.setPack(Integer.valueOf(drug.getPack()));
        }
        LOGGER.info("updateHisDrug 更新后药品信息 organDrug：{}", JSONUtils.toString(organDrug));
        organDrugListDAO.update(organDrug);
    }

    private boolean checkDrugInfo(DrugInfoTO drug) {
        if (null == drug) {
            LOGGER.info("updateHisDrug 当前his的更新药品信息为空！");
            return false;
        }
        if (null == drug.getOrganId()) {
            LOGGER.info("updateHisDrug 当前药品信息，机构信息为空！");
            return false;
        }
        if (null == drug.getDrcode()) {
            LOGGER.info("updateHisDrug 当前药品信息，药品code信息为空！");
            return false;
        }
        if (null == drug.getDrugPrice()) {
            LOGGER.info("updateHisDrug 当前药品信息，药品金额信息为空！");
            return false;
        }
        return true;
    }

    @RpcService
    public String getThirdRecipeUrl(String mpiId) {
        List<PatientBean> patientBeans = iPatientService.findByMpiIdIn(Arrays.asList(mpiId));
        return getThirdUrlString(patientBeans, "", "");
    }

    public String getThirdUrlString(List<PatientBean> patientBeans, String recipeNo, String patientId) {
        String url = "";
        RecipeParameterDao parameterDao = DAOFactory.getDAO(RecipeParameterDao.class);
        if (CollectionUtils.isNotEmpty(patientBeans)) {
            Integer urt = patientBeans.get(0).getUrt();
            PatientService patientService = BasicAPI.getService(PatientService.class);
            List<PatientDTO> patientDTOList = patientService.findPatientByUrt(urt);
            LOGGER.info("recipeService-getThirdRecipeUrl patientBean:{}.", JSONUtils.toString(patientDTOList));
            String pre_url = parameterDao.getByName("yd_thirdurl");
            String yd_hospital_code = parameterDao.getByName("yd_hospital_code");
            List<YdUrlPatient> ydUrlPatients = new ArrayList<>();
            for (PatientDTO patientDTO : patientDTOList) {
                YdUrlPatient ydUrlPatient = new YdUrlPatient();
                String idnum = patientDTO.getIdcard();
                String mobile = patientDTO.getMobile();
                String pname = patientDTO.getPatientName();
                ydUrlPatient.setMobile(mobile);
                ydUrlPatient.setIdnum(idnum);
                ydUrlPatient.setPname(pname);
                if (StringUtils.isNotEmpty(patientId)) {
                    ydUrlPatient.setPno(patientId);
                } else {
                    //查询该用户最新的一条处方记录
                    HospitalRecipeDAO hospitalRecipeDAO = DAOFactory.getDAO(HospitalRecipeDAO.class);
                    List<HospitalRecipe> hospitalRecipes = hospitalRecipeDAO.findByCertificate(patientDTO.getIdcard());
                    if (CollectionUtils.isNotEmpty(hospitalRecipes)) {
                        ydUrlPatient.setPno(hospitalRecipes.get(0).getPatientId());
                    }
                }
                ydUrlPatient.setHisno("");
                ydUrlPatients.add(ydUrlPatient);
            }

            String patient = JSONUtils.toString(ydUrlPatients);
            StringBuilder stringBuilder = new StringBuilder();
            try {
                patient = URLEncoder.encode(patient, "UTF-8");
            } catch (Exception e) {
                LOGGER.error("recipeService-getThirdRecipeUrl url:{}.", JSONUtils.toString(stringBuilder), e);
            }
            stringBuilder.append("?q=").append(patient);
            stringBuilder.append("&h=").append(yd_hospital_code);
            stringBuilder.append("&r=");
            if (StringUtils.isNotEmpty(recipeNo)) {
                stringBuilder.append(recipeNo);
            }
            url = pre_url + stringBuilder.toString();
            LOGGER.info("recipeService-getThirdRecipeUrl url:{}.", JSONUtils.toString(url));
        }
        return url;
    }

    //根据recipeId 判断有没有关联的订单，有订单返回相关的订单id
    //2020春节代码添加
    @RpcService
    public Integer getOrderIdByRecipe(Integer recipeId) {
        LOGGER.info("getOrderIdByRecipe查询处方关联订单，处方id:{}", recipeId);
        //根据recipeId将对应的订单获得，有就返回订单id没有就不返回
        RecipeOrderDAO orderDAO = DAOFactory.getDAO(RecipeOrderDAO.class);
        RecipeOrder order = orderDAO.getRelationOrderByRecipeId(recipeId);
        if (null != order) {
            LOGGER.info("getOrderIdByRecipe当前处方关联上订单");
            return order.getOrderId();
        }
        LOGGER.info("getOrderIdByRecipe当前处方没有关联上订单");
        return null;
    }

    //根据recipeId 判断有没有关联处方是否支持配送
    //2020春节代码添加
    @RpcService
    public Boolean recipeCanDelivery(RecipeBean recipe, List<RecipeDetailBean> details) {
        LOGGER.error("recipeCanDelivery 查询处方是否可配送入参：{},{}.", JSON.toJSONString(recipe), JSON.toJSONString(details));
        boolean flag = false;
        RecipeDAO recipeDAO = getDAO(RecipeDAO.class);
        RecipeDetailDAO recipeDetailDAO = getDAO(RecipeDetailDAO.class);
        RecipeExtendDAO recipeExtendDAO = getDAO(RecipeExtendDAO.class);
        DrugsEnterpriseService drugsEnterpriseService = ApplicationUtils.getRecipeService(DrugsEnterpriseService.class);

        Map<String, Object> rMap = Maps.newHashMap();
        PatientDTO patient = patientService.get(recipe.getMpiid());
        //解决旧版本因为wx2.6患者身份证为null，而业务申请不成功
        if (patient == null || StringUtils.isEmpty(patient.getCertificate())) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "该患者还未填写身份证信息，不能开处方");
        }
        // 就诊人改造：为了确保删除就诊人后历史处方不会丢失，加入主账号用户id
        PatientDTO requestPatient = patientService.getOwnPatientForOtherProject(patient.getLoginId());
        if (null != requestPatient && null != requestPatient.getMpiId()) {
            recipe.setRequestMpiId(requestPatient.getMpiId());
            // urt用于系统消息推送
            recipe.setRequestUrt(requestPatient.getUrt());
        }

        recipe.setStatus(RecipeStatusConstant.UNSIGN);
        recipe.setSignDate(DateTime.now().toDate());
        recipe.setChooseFlag(0);
        recipe.setRemindFlag(0);
        recipe.setPushFlag(0);
        recipe.setTakeMedicine(0);
        recipe.setRecipeMode(null == recipe.getRecipeMode() ? "" : recipe.getRecipeMode());
        recipe.setGiveFlag(null == recipe.getGiveFlag() ? 0 : recipe.getGiveFlag());
        recipe.setPayFlag(null == recipe.getPayFlag() ? 0 : recipe.getPayFlag());
        //date 20200226 添加默认值
        recipe.setTotalMoney(null == recipe.getTotalMoney() ? BigDecimal.ZERO : recipe.getTotalMoney());
        //如果是已经暂存过的处方单，要去数据库取状态 判断能不能进行签名操作
        if (null == recipe || null == details || 0 == details.size()) {
            LOGGER.error("recipeCanDelivery 当前处方或者药品信息不全：{},{}.", JSON.toJSONString(recipe), JSON.toJSONString(details));
            return false;
        }
        Recipe dbrecipe = ObjectCopyUtils.convert(recipe, Recipe.class);
        List<Recipedetail> recipedetails = ObjectCopyUtils.convert(details, Recipedetail.class);
        //设置药品价格
        boolean isSucc = RecipeServiceSub.setDetailsInfo(dbrecipe, recipedetails);
        if (!isSucc) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "recipeCanDelivery-药品详情数据有误");
        }
        Integer recipeId = recipeDAO.updateOrSaveRecipeAndDetail(dbrecipe, recipedetails, false);

        boolean checkEnterprise = drugsEnterpriseService.checkEnterprise(recipe.getClinicOrgan());
        if (checkEnterprise) {
            //药企库存实时查询
            //首先获取机构匹配支持配送的药企列表
            List<Integer> payModeSupport = RecipeServiceSub.getDepSupportMode(RecipeBussConstant.PAYMODE_ONLINE);
            payModeSupport.addAll(RecipeServiceSub.getDepSupportMode(RecipeBussConstant.PAYMODE_COD));

            DrugsEnterpriseDAO drugsEnterpriseDAO = getDAO(DrugsEnterpriseDAO.class);
            //筛选出来的数据已经去掉不支持任何方式配送的药企
            List<DrugsEnterprise> drugsEnterprises = drugsEnterpriseDAO.findByOrganIdAndPayModeSupport(recipe.getClinicOrgan(), payModeSupport);
            if (CollectionUtils.isEmpty(payModeSupport)) {
                LOGGER.error("recipeCanDelivery 处方[{}]的开方机构{}没有配置配送药企.", recipeId, recipe.getClinicOrgan());
                return false;
            } else {
                LOGGER.error("recipeCanDelivery 处方[{}]的开方机构{}获取到配置配送药企：{}.", recipeId, recipe.getClinicOrgan(), JSON.toJSONString(drugsEnterprises));
            }
            RemoteDrugEnterpriseService service = ApplicationUtils.getRecipeService(RemoteDrugEnterpriseService.class);
            YtRemoteService ytRemoteService;
            HdRemoteService hdRemoteService;
            for (DrugsEnterprise drugsEnterprise : drugsEnterprises) {
                AccessDrugEnterpriseService enterpriseService = service.getServiceByDep(drugsEnterprise);
                if (null == enterpriseService) {
                    LOGGER.error("recipeCanDelivery 当前药企没有对接{}.", enterpriseService);
                    continue;
                }
                if (DrugEnterpriseConstant.COMPANY_YT.equals(drugsEnterprise.getCallSys())) {
                    ytRemoteService = (YtRemoteService) enterpriseService;
                    LOGGER.error("recipeCanDelivery 处方[{}]请求药企{}库存", recipeId, drugsEnterprise.getCallSys());
                    if (ytRemoteService.scanStockSend(recipeId, drugsEnterprise)) {
                        flag = true;
                        break;
                    }

                } else if (DrugEnterpriseConstant.COMPANY_HDDYF.equals(drugsEnterprise.getCallSys())) {
                    hdRemoteService = (HdRemoteService) enterpriseService;
                    LOGGER.error("recipeCanDelivery 处方[{}]请求药企{}库存", recipeId, drugsEnterprise.getCallSys());
                    if (hdRemoteService.sendScanStock(recipeId, drugsEnterprise, DrugEnterpriseResult.getFail())) {
                        flag = true;
                        break;
                    }

                } else {
                    LOGGER.error("recipeCanDelivery 处方[{}]请求药企{}库存", recipeId, drugsEnterprise.getCallSys());
                    DrugEnterpriseResult result = service.scanStock(recipeId, drugsEnterprise);
                    boolean succFlag = result.getCode().equals(DrugEnterpriseResult.SUCCESS) ? true : false;
                    if (succFlag) {
                        flag = true;
                        break;
                    }
                }

            }


        }
        if (null != recipeId) {
            LOGGER.info("recipeCanDelivery 处方[{}],删除无用数据中", recipeId);
            recipeDAO.remove(recipeId);
            recipeDetailDAO.deleteByRecipeId(recipeId);
            recipeExtendDAO.remove(recipeId);
        }
        LOGGER.info("recipeCanDelivery 处方[{}],是否支持配送：{}", recipeId, flag);
        return flag;
    }

    /**
     * 开处方时，通过年龄判断是否能够开处方
     * mpiid
     *
     * @return Map<String, Object>
     */
    @RpcService
    public Map<String, Object> findCanRecipeByAge(Map<String, String> params) {
        LOGGER.info("findCanRecipeByAge 参数{}", JSONUtils.toString(params));
        if (StringUtils.isEmpty(params.get("mpiid"))) {
            throw new DAOException("findCanRecipeByAge mpiid不允许为空");
        }
        if (StringUtils.isEmpty(params.get("organId"))) {
            throw new DAOException("findCanRecipeByAge organId不允许为空");
        }
        Map<String, Object> map = Maps.newHashMap();
        boolean canRecipe = false;//默认不可开处方
        //从opbase配置项获取允许开处方患者年龄 findCanRecipeByAge
        IConfigurationCenterUtilsService configService = BaseAPI.getService(IConfigurationCenterUtilsService.class);
        Object findCanRecipeByAge = configService.getConfiguration(Integer.parseInt(params.get("organId")), "findCanRecipeByAge");
        LOGGER.info("findCanRecipeByAge 从opbase配置项获取允许开处方患者年龄{}", findCanRecipeByAge);
        if (findCanRecipeByAge == null) {
            canRecipe = true;//查询不到设置值或默认值或没配置配置项 设置可开处方
        }
        if (!canRecipe) {
            //从opbase获取患者数据
            List<String> findByMpiIdInParam = new ArrayList<>();
            findByMpiIdInParam.add(params.get("mpiid"));
            List<PatientDTO> patientList = patientService.findByMpiIdIn(findByMpiIdInParam);
            if(patientList!=null&&patientList.size()>0){
                //通过生日获取患者年龄
                Integer age = 0;
                try {
                    if(patientList.get(0)!=null&&patientList.get(0).getBirthday()!=null){
                        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
                        age = ChinaIDNumberUtil.getAgeFromBirth(simpleDateFormat.format(patientList.get(0).getBirthday()));
                    }
                    LOGGER.info("findCanRecipeByAge 通过证件号码获取患者年龄{}", age);
                } catch (ValidateException e) {
                    LOGGER.error("findCanRecipeByAge 通过证件号码获取患者年龄异常" + e.getMessage(), e);
                    e.printStackTrace();
                }
                //实际年龄>=配置年龄 设置可开处方
                if (age >= (Integer) findCanRecipeByAge) {
                    canRecipe = true;
                }
            }

        }
        map.put("canRecipe", canRecipe);
        map.put("canRecipeAge", findCanRecipeByAge);
        return map;
    }

//    /**
//     * 根据organid和药剂数获取中药处方代煎费
//     * @return Map<String,Object>
//     */
//    @RpcService
//    public Map<String, Object>   findDecoctionFee(Map<String,String> params) {
//        LOGGER.info("findCanRecipeByAge 参数{}",JSONUtils.toString(params));
//        if(StringUtils.isEmpty(params.get("organId")))   throw new DAOException("findDecoctionAndTCM organId不允许为空");
//        if(StringUtils.isEmpty(params.get("useDays")))   throw new DAOException("findDecoctionAndTCM useDays不允许为空");
//
//        Map<String, Object> map = Maps.newHashMap();
//        BigDecimal decoctionFee=new BigDecimal(0);
//
//        //从opbase配置项获取中药处方每贴代煎费 recipeDecoctionPrice
//        IConfigurationCenterUtilsService configService = BaseAPI.getService(IConfigurationCenterUtilsService.class);
//        Object findRecipeDecoctionPrice = configService.getConfiguration(Integer.parseInt(params.get("organId")), "recipeDecoctionPrice");
//        LOGGER.info("findCanRecipeByAge 从opbase配置项获取中药处方每贴代煎费是{}",findRecipeDecoctionPrice);
//        if(findRecipeDecoctionPrice!=null&& ((BigDecimal)findRecipeDecoctionPrice).compareTo(BigDecimal.ZERO)==1) decoctionFee=((BigDecimal)findRecipeDecoctionPrice).multiply(new BigDecimal(params.get("useDays")));
//
//        if(decoctionFee.compareTo(BigDecimal.ZERO)==-1) decoctionFee=new BigDecimal(0);//金额为负数
//        map.put("decoctionFee",decoctionFee);
//        return map;
//    }
//
    /**
     * 根据organid获取中医辨证论治费
     * @return Map<String,Object>
     */
//    @RpcService
//    public Map<String, Object>   findTCMFee(Map<String,String> params) {
//        LOGGER.info("findCanRecipeByAge 参数{}",JSONUtils.toString(params));
//        if(StringUtils.isEmpty(params.get("organId")))   throw new DAOException("findDecoctionAndTCM organId不允许为空");
//
//        Map<String, Object> map = Maps.newHashMap();
//        BigDecimal TCMFee=new BigDecimal(0);
//
//        IConfigurationCenterUtilsService configService = BaseAPI.getService(IConfigurationCenterUtilsService.class);
//        //从opbase配置项获取中医辨证论治费 recipeTCMPrice
//        Object findRecipeTCMPrice = configService.getConfiguration(Integer.parseInt(params.get("organId")), "recipeTCMPrice");
//        LOGGER.info("findCanRecipeByAge 从opbase配置项获取中医辨证论治费是{}",findRecipeTCMPrice);
//        if(findRecipeTCMPrice!=null&& ((BigDecimal)findRecipeTCMPrice).compareTo(BigDecimal.ZERO)==1) TCMFee=(BigDecimal)findRecipeTCMPrice;
//        map.put("TCMFee",TCMFee);
//        return map;
//    }

    /**
     * 根据organid 获取长处方按钮是否开启、开药天数范围
     *
     * @return Map<String, Object>
     */
    @RpcService
    public Map<String, Object>   findisCanOpenLongRecipeAndUseDayRange(Map<String,String> params) {
        LOGGER.info("findisCanOpenLongRecipeAndUseDayRange 参数{}",JSONUtils.toString(params));
        if(StringUtils.isEmpty(params.get("organId"))) {
            throw new DAOException("findUseDayRange organId不允许为空");
        }
        Map<String, Object> map = Maps.newHashMap();

        //获取长处方配置
        IConfigurationCenterUtilsService configService = BaseAPI.getService(IConfigurationCenterUtilsService.class);
        Object isCanOpenLongRecipe = configService.getConfiguration(Integer.parseInt(params.get("organId")), "isCanOpenLongRecipe");
        LOGGER.info("findisCanOpenLongRecipeAndUseDayRange 从opbase配置项获取是否能开长处方{}",isCanOpenLongRecipe);
        if(isCanOpenLongRecipe==null||!(boolean)isCanOpenLongRecipe){//按钮没配置或关闭
        }
        if((boolean)isCanOpenLongRecipe){//按钮开启
            Object yesLongRecipe = configService.getConfiguration(Integer.parseInt(params.get("organId")), "yesLongRecipe");
            LOGGER.info("findisCanOpenLongRecipeAndUseDayRange 从opbase配置项获取长处方开药天数范围是{}",yesLongRecipe==null?yesLongRecipe:((String)yesLongRecipe).replace(",","-"));
            map.put("longTimeRange",yesLongRecipe==null?yesLongRecipe:((String)yesLongRecipe).replace(",","-"));
            Object noLongRecipe = configService.getConfiguration(Integer.parseInt(params.get("organId")), "noLongRecipe");
            LOGGER.info("findisCanOpenLongRecipeAndUseDayRange 从opbase配置项获取非长处方开药天数范围是{}",noLongRecipe==null?noLongRecipe:((String)noLongRecipe).replace(",","-"));
            map.put("shortTimeRange",noLongRecipe==null?noLongRecipe:((String)noLongRecipe).replace(",","-"));
        }
        map.put("canOpenLongRecipe",isCanOpenLongRecipe);

        //获取用药天数配置
        Object isLimitUseDays = configService.getConfiguration(Integer.parseInt(params.get("organId")), "isLimitUseDays");
        LOGGER.info("findisCanOpenLongRecipeAndUseDayRange 从opbase配置项获取是否开启用药天数配置{}",isLimitUseDays);
        if((boolean)isLimitUseDays){//按钮开启
            Object useDaysRange = configService.getConfiguration(Integer.parseInt(params.get("organId")), "useDaysRange");
            LOGGER.info("findisCanOpenLongRecipeAndUseDayRange 从opbase配置项获取用药天数配置天数范围是{}",useDaysRange==null?useDaysRange:((String)useDaysRange).replace(",","-"));
            map.put("useDaysRange",useDaysRange==null?useDaysRange:((String)useDaysRange).replace(",","-"));
        }
        map.put("limitUseDays",isLimitUseDays);

        return map;
    }


    @RpcService
    private Map<String, String> getRevisitType() {
        Map<String, String> map = new HashMap<>();
        map.put("0", "自费");
        map.put("1", "普通保险");
        map.put("2", "门特保险");
        return map;
    }


    public void signRecipeInfoSave(Integer recipeId, boolean isDoctor, CaSignResultVo signResultVo, Integer organId) {
        try {
            IConfigurationCenterUtilsService configurationService = BaseAPI.getService(IConfigurationCenterUtilsService.class);
            String thirdCASign = (String) configurationService.getConfiguration(organId, "thirdCASign");
            //上海儿童特殊处理
            String value = ParamUtils.getParam("SH_CA_ORGANID_WHITE_LIST");
            List<String> caList = Arrays.asList(value.split(","));
            if (caList.contains(organId + "")) {
                thirdCASign = "shanghaiCA";
            }
            signRecipeInfoService.saveSignInfo(recipeId, isDoctor, signResultVo, thirdCASign);
        } catch (Exception e) {
            LOGGER.info("signRecipeInfoService error recipeId[{}] errorMsg[{}]", recipeId, e.getMessage(), e);
        }
    }

    public void doAfterCheckNotPassYs(Recipe recipe) {
        boolean secondsignflag = RecipeServiceSub.canSecondAudit(recipe.getClinicOrgan());
        /*IOrganConfigService iOrganConfigService = ApplicationUtils.getBaseService(IOrganConfigService.class);
        boolean secondsignflag = iOrganConfigService.getEnableSecondsignByOrganId(recipe.getClinicOrgan());*/
        //不支持二次签名的机构直接执行后续操作
        if (!secondsignflag) {
            //一次审核不通过的需要将优惠券释放
            RecipeCouponService recipeCouponService = ApplicationUtils.getRecipeService(RecipeCouponService.class);
            recipeCouponService.unuseCouponByRecipeId(recipe.getRecipeId());
            //TODO 根据审方模式改变
            auditModeContext.getAuditModes(recipe.getReviewType()).afterCheckNotPassYs(recipe);
            //HIS消息发送
            //审核不通过 往his更新状态（已取消）
            RecipeHisService hisService = ApplicationUtils.getRecipeService(RecipeHisService.class);
            hisService.recipeStatusUpdate(recipe.getRecipeId());
            //记录日志
            RecipeLogService.saveRecipeLog(recipe.getRecipeId(), recipe.getStatus(), recipe.getStatus(), "审核不通过处理完成");
        } else {
            //需要二次审核，这里是一次审核不通过的流程
            //需要将处方的审核状态设置成一次审核不通过的状态
            Map<String, Object> updateMap = new HashMap<>();
            RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
            updateMap.put("checkStatus", RecipecCheckStatusConstant.First_Check_No_Pass);
            recipeDAO.updateRecipeInfoByRecipeId(recipe.getRecipeId(), updateMap);
        }
        //由于支持二次签名的机构第一次审方不通过时医生收不到消息。所以将审核不通过推送消息放这里处理
        sendCheckNotPassYsMsg(recipe);
    }

    private void sendCheckNotPassYsMsg(Recipe recipe) {
        RecipeDAO rDao = DAOFactory.getDAO(RecipeDAO.class);
        if (null == recipe) {
            return;
        }
        recipe = rDao.get(recipe.getRecipeId());
        if (RecipeBussConstant.FROMFLAG_HIS_USE.equals(recipe.getFromflag())) {
            //发送审核不成功消息
            //${sendOrgan}：抱歉，您的处方未通过药师审核。如有收取费用，款项将为您退回，预计1-5个工作日到账。如有疑问，请联系开方医生或拨打${customerTel}联系小纳。
            RecipeMsgService.sendRecipeMsg(RecipeMsgEnum.RECIPE_YS_CHECKNOTPASS_4HIS, recipe);
            //date 2019/10/10
            //添加判断 一次审核不通过不需要向患者发送消息
        } else if (RecipeBussConstant.FROMFLAG_PLATFORM.equals(recipe.getFromflag())) {
            //发送审核不成功消息
            //处方审核不通过通知您的处方单审核不通过，如有疑问，请联系开方医生
            RecipeMsgService.batchSendMsg(recipe, eh.cdr.constant.RecipeStatusConstant.CHECK_NOT_PASSYS_REACHPAY);
        }
    }

    @RpcService
    public String queryRecipeGetUrl(Integer recipeId) {
        //根据选中的处方信息，获取对应处方的处方笺医院的url

        //根据当前机构配置的pdfurl组装处方信息到动态外链上
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        Recipe recipe = recipeDAO.getByRecipeId(recipeId);
        if(null == recipe){
            LOGGER.warn("queryRecipeGetUrl-当前处方{}不存在", recipeId);
            return null;
        }
        RecipeExtendDAO extendDAO = DAOFactory.getDAO(RecipeExtendDAO.class);
        RecipeExtend recipeExtend = extendDAO.getByRecipeId(recipeId);
        if(null == recipeExtend){
            LOGGER.warn("queryRecipeGetUrl-当前处方扩展信息{}不存在", recipeId);
        }
        //获取当前机构配置的处方笺下载链接
        Object downPrescriptionUrl = configService.getConfiguration(recipe.getClinicOrgan(), "downPrescriptionUrl");
        if(null == downPrescriptionUrl){
            LOGGER.warn("queryRecipeGetUrl-当前机构下{}获取处方笺url的配置为空", recipe.getClinicOrgan());
            return null;
        }
        //根据recipe信息组装url的动态链接
        Map<String, Object> paramMap = Maps.newHashMap();
        paramMap.put("registerID", null != recipeExtend ? recipeExtend.getRegisterID() : null);
        paramMap.put("recipeCode", recipe.getRecipeCode());
        paramMap.put("patientID", recipe.getPatientID());
        paramMap.put("cardNo", null != recipeExtend ? recipeExtend.getCardNo() : null);
        paramMap.put("cardType", null != recipeExtend ? recipeExtend.getCardType() : null);
        LOGGER.info("queryRecipeGetUrl-当前处方动态外链组装的入参{}", JSONObject.toJSONString(paramMap));
        String resultUrl = LocalStringUtil.processTemplate((String) downPrescriptionUrl, paramMap);
        return resultUrl;
    }


    /**
     * 定时任务:定时取消处方的
     */
    @RpcService
    public void cancelSignRecipeTask() {
        RecipeOrderDAO orderDAO = getDAO(RecipeOrderDAO.class);
        RecipeDAO recipeDAO = getDAO(RecipeDAO.class);
        RecipeOrderService orderService = ApplicationUtils.getRecipeService(RecipeOrderService.class);
        RecipeOrderDAO recipeOrderDAO = getDAO(RecipeOrderDAO.class);
        RecipeHisService hisService = ApplicationUtils.getRecipeService(RecipeHisService.class);

        //设置查询时间段
        String endDt = DateConversion.getDateFormatter(DateConversion.getDateTimeDaysAgo(Integer.parseInt(cacheService.getParam(ParameterConstant.KEY_RECIPE_VALIDDATE_DAYS, RECIPE_EXPIRED_DAYS.toString()))), DateConversion.DEFAULT_DATE_TIME);
        String startDt = DateConversion.getDateFormatter(DateConversion.getDateTimeDaysAgo(Integer.parseInt(cacheService.getParam(ParameterConstant.KEY_RECIPE_CANCEL_DAYS, RECIPE_EXPIRED_SEARCH_DAYS.toString()))), DateConversion.DEFAULT_DATE_TIME);

        //筛选处方状态是签名中和签名失败的
        List<Recipe> recipeList = recipeDAO.getRecipeListForSignCancelRecipe(startDt, endDt);
        //这里要取消处方的首先判断处方的状态是
        //取消处方的步骤：1.判断处方
        LOGGER.info("cancelSignRecipeTask 取消的ca签名处方列表{}", JSONUtils.toString(recipeList));
        RecipeOrder order = new RecipeOrder();
        StringBuilder memo = new StringBuilder();
        Integer status;
        if (CollectionUtils.isNotEmpty(recipeList)) {
            for (Recipe recipe : recipeList) {
                if (RecipeBussConstant.RECIPEMODE_ZJJGPT.equals(recipe.getRecipeMode())) {
                    OrganAndDrugsepRelationDAO organAndDrugsepRelationDAO = DAOFactory.getDAO(OrganAndDrugsepRelationDAO.class);
                    List<DrugsEnterprise> drugsEnterprises = organAndDrugsepRelationDAO.findDrugsEnterpriseByOrganIdAndStatus(recipe.getClinicOrgan(), 1);
                    for (DrugsEnterprise drugsEnterprise : drugsEnterprises) {
                        if (("aldyf".equals(drugsEnterprise.getCallSys()) || "tmdyf".equals(drugsEnterprise.getCallSys())) && recipe.getPushFlag() == 1) {
                            //向药企推送处方过期的通知
                            RemoteDrugEnterpriseService remoteDrugEnterpriseService = ApplicationUtils.getRecipeService(RemoteDrugEnterpriseService.class);
                            try {
                                AccessDrugEnterpriseService remoteService = remoteDrugEnterpriseService.getServiceByDep(drugsEnterprise);
                                DrugEnterpriseResult drugEnterpriseResult = remoteService.updatePrescriptionStatus(recipe.getRecipeCode(), AlDyfRecipeStatusConstant.EXPIRE);
                                LOGGER.info("向药企推送处方过期通知,{}", JSONUtils.toString(drugEnterpriseResult));
                            } catch (Exception e) {
                                LOGGER.info("向药企推送处方过期通知有问题{}", recipe.getRecipeId(), e);
                            }
                        }


                    }
                }
                memo.delete(0, memo.length());
                int recipeId = recipe.getRecipeId();
                //相应订单处理
                order = orderDAO.getOrderByRecipeId(recipeId);
                if(null != order){
                    orderService.cancelOrder(order, OrderStatusConstant.CANCEL_AUTO, true);
                    if (recipe.getFromflag().equals(RecipeBussConstant.FROMFLAG_HIS_USE)) {
                        orderDAO.updateByOrdeCode(order.getOrderCode(), ImmutableMap.of("cancelReason", "患者未在规定时间内支付，该处方单已失效"));
                        //发送超时取消消息
                        //${sendOrgan}：抱歉，您的处方单由于超过${overtime}未处理，处方单已失效。如有疑问，请联系开方医生或拨打${customerTel}联系小纳。
                        RecipeMsgService.sendRecipeMsg(RecipeMsgEnum.RECIPE_CANCEL_4HIS, recipe);
                    }
                }


                //变更处方状态
                status = recipe.getStatus();
                //date 20200709 修改前置的处方药师ca签名中签名失败，处方状态未处理
                if(ReviewTypeConstant.Preposition_Check.equals(recipe.getReviewType()) && (RecipeStatusConstant.SIGN_ING_CODE_PHA == status || RecipeStatusConstant.SIGN_ERROR_CODE_PHA == status)){
                    recipeDAO.updateRecipeInfoByRecipeId(recipeId, RecipeStatusConstant.NO_OPERATOR, ImmutableMap.of("chooseFlag", 1));
                }else{
                    recipeDAO.updateRecipeInfoByRecipeId(recipeId, RecipeStatusConstant.DELETE, ImmutableMap.of("chooseFlag", 1));
                }

                memo.append("当前处方ca操作超时没处理，失效删除");
                //未支付，三天后自动取消后，优惠券自动释放
                RecipeCouponService recipeCouponService = ApplicationUtils.getRecipeService(RecipeCouponService.class);
                recipeCouponService.unuseCouponByRecipeId(recipeId);
                //推送处方到监管平台
                RecipeBusiThreadPool.submit(new PushRecipeToRegulationCallable(recipe.getRecipeId(), 1));
                //HIS消息发送
                boolean succFlag = hisService.recipeStatusUpdate(recipeId);
                if (succFlag) {
                    memo.append(",HIS推送成功");
                } else {
                    memo.append(",HIS推送失败");
                }
                //保存处方状态变更日志
                RecipeLogService.saveRecipeLog(recipeId, status, RecipeStatusConstant.DELETE, memo.toString());

            }
        }

    }

    /**
     * 取消ca处方过期包含过期时间
     */
    @RpcService(timeout = 600000)
    public void cancelSignRecipe(Integer endDate, Integer startDate) {
        RecipeOrderDAO orderDAO = getDAO(RecipeOrderDAO.class);
        RecipeDAO recipeDAO = getDAO(RecipeDAO.class);
        RecipeOrderService orderService = ApplicationUtils.getRecipeService(RecipeOrderService.class);
        RecipeOrderDAO recipeOrderDAO = getDAO(RecipeOrderDAO.class);
        RecipeHisService hisService = ApplicationUtils.getRecipeService(RecipeHisService.class);

        //设置查询时间段
        String endDt = DateConversion.getDateFormatter(DateConversion.getDateTimeDaysAgo(endDate), DateConversion.DEFAULT_DATE_TIME);
        String startDt = DateConversion.getDateFormatter(DateConversion.getDateTimeDaysAgo(startDate), DateConversion.DEFAULT_DATE_TIME);

        //筛选处方状态是签名中和签名失败的
        List<Recipe> recipeList = recipeDAO.getRecipeListForSignCancelRecipe(startDt, endDt);
        //这里要取消处方的首先判断处方的状态是
        //取消处方的步骤：1.判断处方
        LOGGER.info("cancelSignRecipeTask 取消的ca签名处方列表{}", JSONUtils.toString(recipeList));
        RecipeOrder order = null;
        StringBuilder memo = new StringBuilder();
        Integer status;
        if (CollectionUtils.isNotEmpty(recipeList)) {
            for (Recipe recipe : recipeList) {
                if (RecipeBussConstant.RECIPEMODE_ZJJGPT.equals(recipe.getRecipeMode())) {
                    OrganAndDrugsepRelationDAO organAndDrugsepRelationDAO = DAOFactory.getDAO(OrganAndDrugsepRelationDAO.class);
                    List<DrugsEnterprise> drugsEnterprises = organAndDrugsepRelationDAO.findDrugsEnterpriseByOrganIdAndStatus(recipe.getClinicOrgan(), 1);
                    for (DrugsEnterprise drugsEnterprise : drugsEnterprises) {
                        if (("aldyf".equals(drugsEnterprise.getCallSys()) || "tmdyf".equals(drugsEnterprise.getCallSys())) && recipe.getPushFlag() == 1) {
                            //向药企推送处方过期的通知
                            RemoteDrugEnterpriseService remoteDrugEnterpriseService = ApplicationUtils.getRecipeService(RemoteDrugEnterpriseService.class);
                            try {
                                AccessDrugEnterpriseService remoteService = remoteDrugEnterpriseService.getServiceByDep(drugsEnterprise);
                                DrugEnterpriseResult drugEnterpriseResult = remoteService.updatePrescriptionStatus(recipe.getRecipeCode(), AlDyfRecipeStatusConstant.EXPIRE);
                                LOGGER.info("向药企推送处方过期通知,{}", JSONUtils.toString(drugEnterpriseResult));
                            } catch (Exception e) {
                                LOGGER.info("向药企推送处方过期通知有问题{}", recipe.getRecipeId(), e);
                            }
                        }


                    }
                }
                memo.delete(0, memo.length());
                int recipeId = recipe.getRecipeId();
                //相应订单处理
                order = orderDAO.getOrderByRecipeId(recipeId);
                if(null != order){

                    orderService.cancelOrder(order, OrderStatusConstant.CANCEL_AUTO, true);
                    if (recipe.getFromflag().equals(RecipeBussConstant.FROMFLAG_HIS_USE)) {
                        orderDAO.updateByOrdeCode(order.getOrderCode(), ImmutableMap.of("cancelReason", "患者未在规定时间内支付，该处方单已失效"));
                        //发送超时取消消息
                        //${sendOrgan}：抱歉，您的处方单由于超过${overtime}未处理，处方单已失效。如有疑问，请联系开方医生或拨打${customerTel}联系小纳。
                        RecipeMsgService.sendRecipeMsg(RecipeMsgEnum.RECIPE_CANCEL_4HIS, recipe);
                    }
                }

                //变更处方状态
                status = recipe.getStatus();
                recipeDAO.updateRecipeInfoByRecipeId(recipeId, RecipeStatusConstant.DELETE, ImmutableMap.of("chooseFlag", 1));

                memo.append("当前处方ca操作超时没处理，失效删除");
                //未支付，三天后自动取消后，优惠券自动释放
                RecipeCouponService recipeCouponService = ApplicationUtils.getRecipeService(RecipeCouponService.class);
                recipeCouponService.unuseCouponByRecipeId(recipeId);
                //推送处方到监管平台
                RecipeBusiThreadPool.submit(new PushRecipeToRegulationCallable(recipe.getRecipeId(), 1));
                //HIS消息发送
                boolean succFlag = hisService.recipeStatusUpdate(recipeId);
                if (succFlag) {
                    memo.append(",HIS推送成功");
                } else {
                    memo.append(",HIS推送失败");
                }
                //保存处方状态变更日志
                RecipeLogService.saveRecipeLog(recipeId, status, RecipeStatusConstant.DELETE, memo.toString());

            }
        }

        //处理过期取消的处方
        List<Integer> statusList = Arrays.asList(RecipeStatusConstant.NO_PAY, RecipeStatusConstant.NO_OPERATOR);
        for (Integer statusCancel : statusList) {
            List<Recipe> recipeCancelList = recipeDAO.getRecipeListForCancelRecipe(statusCancel, startDt, endDt);
            LOGGER.info("cancelRecipeTask 状态=[{}], 取消数量=[{}], 详情={}", statusCancel, recipeCancelList.size(), JSONUtils.toString(recipeCancelList));
            if (CollectionUtils.isNotEmpty(recipeCancelList)) {
                for (Recipe recipe : recipeCancelList) {
                    if (RecipeBussConstant.RECIPEMODE_ZJJGPT.equals(recipe.getRecipeMode())) {
                        OrganAndDrugsepRelationDAO organAndDrugsepRelationDAO = DAOFactory.getDAO(OrganAndDrugsepRelationDAO.class);
                        List<DrugsEnterprise> drugsEnterprises = organAndDrugsepRelationDAO.findDrugsEnterpriseByOrganIdAndStatus(recipe.getClinicOrgan(), 1);
                        for (DrugsEnterprise drugsEnterprise : drugsEnterprises) {
                            if ("aldyf".equals(drugsEnterprise.getCallSys()) || ("tmdyf".equals(drugsEnterprise.getCallSys()) && recipe.getPushFlag() == 1)) {
                                //向药企推送处方过期的通知
                                RemoteDrugEnterpriseService remoteDrugEnterpriseService = ApplicationUtils.getRecipeService(RemoteDrugEnterpriseService.class);
                                try {
                                    AccessDrugEnterpriseService remoteService = remoteDrugEnterpriseService.getServiceByDep(drugsEnterprise);
                                    DrugEnterpriseResult drugEnterpriseResult = remoteService.updatePrescriptionStatus(recipe.getRecipeCode(), AlDyfRecipeStatusConstant.EXPIRE);
                                    LOGGER.info("向药企推送处方过期通知,{}", JSONUtils.toString(drugEnterpriseResult));
                                } catch (Exception e) {
                                    LOGGER.info("向药企推送处方过期通知有问题{}", recipe.getRecipeId(), e);
                                }
                            }


                        }
                    }
                    memo.delete(0, memo.length());
                    int recipeId = recipe.getRecipeId();
                    //相应订单处理
                    order = orderDAO.getOrderByRecipeId(recipeId);
                    orderService.cancelOrder(order, OrderStatusConstant.CANCEL_AUTO, true);
                    if (recipe.getFromflag().equals(RecipeBussConstant.FROMFLAG_HIS_USE)) {
                        if(null != order){
                            orderDAO.updateByOrdeCode(order.getOrderCode(), ImmutableMap.of("cancelReason", "患者未在规定时间内支付，该处方单已失效"));
                        }
                        //发送超时取消消息
                        //${sendOrgan}：抱歉，您的处方单由于超过${overtime}未处理，处方单已失效。如有疑问，请联系开方医生或拨打${customerTel}联系小纳。
                        RecipeMsgService.sendRecipeMsg(RecipeMsgEnum.RECIPE_CANCEL_4HIS, recipe);
                    }

                    //变更处方状态
                    recipeDAO.updateRecipeInfoByRecipeId(recipeId, statusCancel, ImmutableMap.of("chooseFlag", 1));
                    RecipeMsgService.batchSendMsg(recipe, statusCancel);
                    if (RecipeStatusConstant.NO_PAY == statusCancel) {
                        memo.append("已取消,超过3天未支付");
                    } else if (RecipeStatusConstant.NO_OPERATOR == statusCancel) {
                        memo.append("已取消,超过3天未操作");
                    } else {
                        memo.append("未知状态:" + statusCancel);
                    }
                    if (RecipeStatusConstant.NO_PAY == statusCancel) {
                        //未支付，三天后自动取消后，优惠券自动释放
                        RecipeCouponService recipeCouponService = ApplicationUtils.getRecipeService(RecipeCouponService.class);
                        recipeCouponService.unuseCouponByRecipeId(recipeId);
                    }
                    //推送处方到监管平台
                    RecipeBusiThreadPool.submit(new PushRecipeToRegulationCallable(recipe.getRecipeId(), 1));
                    //HIS消息发送
                    boolean succFlag = hisService.recipeStatusUpdate(recipeId);
                    if (succFlag) {
                        memo.append(",HIS推送成功");
                    } else {
                        memo.append(",HIS推送失败");
                    }
                    //保存处方状态变更日志
                    RecipeLogService.saveRecipeLog(recipeId, RecipeStatusConstant.CHECK_PASS, statusCancel, memo.toString());
                }
            }
        }

    }

    @RpcService
    public List<String> findCommonSymptomIdByDoctorAndOrganId(int doctorId, int organId) {
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        return recipeDAO.findCommonSymptomIdByDoctorAndOrganId(doctorId, organId);
    }


    @RpcService
    public List<Symptom> findCommonSymptomByDoctorAndOrganId(int doctor, int organId) {
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        return recipeDAO.findCommonSymptomByDoctorAndOrganId(doctor, organId, 0, 10);
    }

    /**
     * 根据 第三方id 与 状态 获取最新处方id
     *
     * @param clinicId 第三方关联id （目前只有复诊）
     * @param status   处方状态
     * @return
     */
    @RpcService
    public Integer getRecipeIdByClinicId(Integer clinicId, Integer status) {
        LOGGER.info("RecipeService.getRecipeByClinicId clinicId={}", clinicId);
        return Optional.ofNullable(recipeDAO.getByClinicIdAndStatus(clinicId, status)).map(Recipe::getRecipeId).orElse(null);
    }

    @RpcService
    public void pharmacyToRecipePDF(Integer recipeId){
        //再触发药师签名的时候将pdf先生成，回调的时候再将CA的返回更新
        //之所以不放置在CA回调里，是因为老流程里不是一定调用回调函数的
        //date 202001013 修改非易签保流程下的pdf
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        Recipe recipe = recipeDAO.getByRecipeId(recipeId);
        String fileId;
        if(null == recipe){
            LOGGER.warn("当前处方{}信息为null，生成药师pdf部分失败", recipeId);
            return;
        }
        DoctorDTO doctorDTOn = doctorService.getByDoctorId(recipe.getChecker());
        if(null == doctorDTOn){
            LOGGER.warn("当前处方{}信息药师审核信息为空，生成药师pdf部分失败", recipeId);
            return;
        }
        try {
            boolean usePlatform = true;
            Object recipeUsePlatformCAPDF = configService.getConfiguration(recipe.getClinicOrgan(), "recipeUsePlatformCAPDF");
            if(null != recipeUsePlatformCAPDF){
                usePlatform = Boolean.parseBoolean(recipeUsePlatformCAPDF.toString());
            }
            //使用平台CA模式，手动生成pdf
            //生成pdf分解成，先生成无医生药师签名的pdf，再将医生药师的签名放置在pdf上
            String pdfBase64Str;
            String signImageId;
            if(usePlatform) {
                CaSealRequestTO requestSealTO = RecipeServiceEsignExt.signCreateRecipePDF(recipeId, false);
                if (null == requestSealTO) {
                    LOGGER.warn("当前处方{}CA组装【pdf】和【签章数据】信息返回空, 产生CA模板pdf文件失败！", recipeId);
                } else {
                    //先将产生的pdf
                    signImageId = doctorDTOn.getSignImage();
                    String sealDataFrom = (String) configService.getConfiguration(recipe.getClinicOrgan(), "sealDataFrom");
                    //根据ca配置：签章显示是显示第三方的签章还是平台签章，默认使用平台签章
                    if("thirdSeal".equals(sealDataFrom)){
                        LOGGER.info("使用第三方签名，recipeId:{}",recipeId);
                        SignRecipeInfoService signRecipeInfoService = AppContextHolder.getBean("signRecipeInfoService", SignRecipeInfoService.class);
                        SignDoctorRecipeInfo phaInfo = signRecipeInfoService.getSignInfoByRecipeIdAndServerType(recipeId, CARecipeTypeConstant.CA_RECIPE_PHA);
                        if (null != phaInfo) {
                            signImageId = phaInfo.getSignPictureDoc();
                        }
                    }
                    pdfBase64Str = requestSealTO.getPdfBase64Str();
                    //将生成的处方pdf生成id
                    fileId = CreateRecipePdfUtil.generateDocSignImageInRecipePdf(recipeId, recipe.getChecker(),
                            false, TCM_TEMPLATETYPE.equals(recipe.getRecipeType()),pdfBase64Str, signImageId);
                    RecipeServiceEsignExt.saveSignRecipePDFCA(null, recipeId, null, null, null, false, fileId);
                }
            }

        } catch (Exception e) {
            LOGGER.warn("当前处方{}是使用平台药师部分pdf的,生成失败！", recipe.getRecipeId());
            //日志记录
            RecipeLogService.saveRecipeLog(recipeId, recipe.getStatus(), recipe.getStatus(), "平台药师部分pdf的生成失败");
        }
    }

    @RpcService
    public void aa(int pdfId) throws Exception {
//        RecipeBusiThreadPool.execute(new UpdateReceiverInfoRecipePdfRunable(recipeId));
//        RecipeBusiThreadPool.execute(new UpdateWaterPrintRecipePdfRunable(recipeId));
//        RecipeCAService a=new RecipeCAService();
//        a.updateWaterPrintRecipePdfRunable(recipeId);
       // generateRecipePdfAndSign(223829);
//        IFileDownloadService fileDownloadService = ApplicationUtils.getBaseService(IFileDownloadService.class);
//        InputStream input = new ByteArrayInputStream(fileDownloadService.downloadAsByte(pdfId));
//        String pdfBase64String=new BufferedReader(new InputStreamReader(input))
//                .lines().collect(Collectors.joining(System.lineSeparator()));;
//        CreateRecipePdfUtil.generateDocSignImageInRecipePdf1(223829,1,true,false,input,"5fa103037826c65418509d36");
//
        doctorToRecipePDF(pdfId);
        //new CaAfterProcessType().hisCallBackCARecipeFunction(pdfId);
    }

}
