package recipe.service;

import com.alibaba.fastjson.JSON;
import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.ngari.base.BaseAPI;
import com.ngari.base.patient.model.PatientBean;
import com.ngari.base.patient.service.IPatientService;
import com.ngari.base.property.service.IConfigurationCenterUtilsService;
import com.ngari.patient.dto.PatientDTO;
import com.ngari.patient.service.DoctorService;
import com.ngari.patient.service.PatientService;
import com.ngari.patient.utils.ObjectCopyUtils;
import com.ngari.recipe.basic.ds.PatientVO;
import com.ngari.recipe.common.RecipePatientRefundVO;
import com.ngari.recipe.common.RecipeResultBean;
import com.ngari.recipe.entity.*;
import com.ngari.recipe.recipe.model.*;
import com.ngari.recipe.recipeorder.model.RecipeOrderBean;
import ctd.controller.exception.ControllerException;
import ctd.dictionary.Dictionary;
import ctd.dictionary.DictionaryController;
import ctd.persistence.DAOFactory;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcBean;
import ctd.util.annotation.RpcService;
import ctd.util.event.GlobalEventExecFactory;
import eh.wxpay.constant.PayConstant;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.util.Assert;
import recipe.ApplicationUtils;
import recipe.bussutil.RecipeUtil;
import recipe.constant.*;
import recipe.dao.*;
import recipe.dao.bean.PatientRecipeBean;
import recipe.dao.bean.RecipeRollingInfo;
import recipe.service.common.RecipeCacheService;
import recipe.util.DateConversion;
import recipe.util.MapValueUtil;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static recipe.service.RecipeServiceSub.convertRecipeForRAP;
import static recipe.service.RecipeServiceSub.convertSensitivePatientForRAP;

/**
 * 处方业务一些列表查询
 * company: ngarihealth
 *
 * @author: 0184/yu_yun
 * @date:2017/2/13.
 */
@RpcBean("recipeListService")
public class RecipeListService extends RecipeBaseService{

    private static final Logger LOGGER = LoggerFactory.getLogger(RecipeListService.class);

    public static final String LIST_TYPE_RECIPE = "1";

    public static final String LIST_TYPE_ORDER = "2";

    public static final Integer RECIPE_PAGE = 0;

    public static final Integer ORDER_PAGE = 1;

    //历史处方显示的状态：未处理、未支付、审核不通过、失败、已完成、his失败、取药失败
    //date 20191016
    //历史处方展示的状态不包含已删除，已撤销，同步his失败（原已取消状态）
    //date 20191126
    //添加上已撤销的处方
    public static final Integer[] HistoryRecipeListShowStatusList = {RecipeStatusConstant.NO_OPERATOR,
            RecipeStatusConstant.NO_PAY, RecipeStatusConstant.CHECK_NOT_PASS_YS, RecipeStatusConstant.RECIPE_FAIL, RecipeStatusConstant.FINISH, RecipeStatusConstant.NO_DRUG, RecipeStatusConstant.REVOKE};

    public static final Integer No_Show_Button = 3;

    /**
     * 医生端处方列表展示
     *
     * @param doctorId 医生ID
     * @param recipeId 上一页最后一条处方ID，首页传0
     * @param limit    每页限制数
     * @return
     */
    @RpcService
    public List<Map<String, Object>> findRecipesForDoctor(Integer doctorId, Integer recipeId, Integer limit) {
        Assert.notNull(doctorId, "findRecipesForDoctor doctor is null.");
        checkUserHasPermissionByDoctorId(doctorId);
        recipeId = (null == recipeId || Integer.valueOf(0).equals(recipeId)) ? Integer.valueOf(Integer.MAX_VALUE) : recipeId;

        List<Map<String, Object>> list = new ArrayList<>(0);
        PatientService patientService = ApplicationUtils.getBasicService(PatientService.class);
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        RecipeDetailDAO recipeDetailDAO = DAOFactory.getDAO(RecipeDetailDAO.class);
        RecipeOrderDAO orderDAO = DAOFactory.getDAO(RecipeOrderDAO.class);
        OrganDrugListDAO organDrugListDAO = DAOFactory.getDAO(OrganDrugListDAO.class);

        List<Recipe> recipeList = recipeDAO.findRecipesForDoctor(doctorId, recipeId, 0, limit);
        LOGGER.info("findRecipesForDoctor recipeList size={}", recipeList.size());
        if (CollectionUtils.isNotEmpty(recipeList)) {
            List<String> patientIds = new ArrayList<>(0);
            Map<Integer, RecipeBean> recipeMap = Maps.newHashMap();
            //date 20200506
            //获取处方对应的订单信息
            Map<String, Integer> orderStatus = new HashMap<>();
            List<String> recipeCodes = recipeList.stream().map(recipe -> recipe.getOrderCode()).filter(code -> StringUtils.isNotEmpty(code)).collect(Collectors.toList());
            if(CollectionUtils.isNotEmpty(recipeCodes)){

                List<RecipeOrder> recipeOrders = orderDAO.findValidListbyCodes(recipeCodes);
                orderStatus = recipeOrders.stream().collect(Collectors.toMap(RecipeOrder::getOrderCode, RecipeOrder::getStatus));
            }

            for (Recipe recipe : recipeList) {
                if (StringUtils.isNotEmpty(recipe.getMpiid())) {
                    patientIds.add(recipe.getMpiid());
                }
                //设置处方具体药品名称
                List<Recipedetail> recipedetails = recipeDetailDAO.findByRecipeId(recipe.getRecipeId());
                StringBuilder stringBuilder = new StringBuilder();

                for (Recipedetail recipedetail : recipedetails) {
                    List<OrganDrugList> organDrugLists = organDrugListDAO.findByDrugIdAndOrganId(recipedetail.getDrugId(), recipe.getClinicOrgan());
                    if (organDrugLists != null && 0 < organDrugLists.size()) {
                        stringBuilder.append(organDrugLists.get(0).getSaleName());
                        if (StringUtils.isNotEmpty(organDrugLists.get(0).getDrugForm())) {
                            stringBuilder.append(organDrugLists.get(0).getDrugForm());
                        }
                    } else {
                        stringBuilder.append(recipedetail.getDrugName());
                    }
                    stringBuilder.append(" ").append(recipedetail.getDrugSpec()).append("/").append(recipedetail.getDrugUnit()).append("、");
                }
                if(-1 != stringBuilder.lastIndexOf("、")){
                    stringBuilder.deleteCharAt(stringBuilder.lastIndexOf("、"));
                }
                recipe.setRecipeDrugName(stringBuilder.toString());
                //前台页面展示的时间源不同
                recipe.setRecipeShowTime(recipe.getCreateDate());
                boolean effective = false;
                //只有审核未通过的情况需要看订单状态
                if (RecipeStatusConstant.CHECK_NOT_PASS_YS == recipe.getStatus()) {
                    effective = orderDAO.isEffectiveOrder(recipe.getOrderCode(), recipe.getPayMode());
                }
                //Map<String, String> tipMap = RecipeServiceSub.getTipsByStatus(recipe.getStatus(), recipe, effective);
                //date 20190929
                //修改医生端状态文案显示
                Map<String, String> tipMap =
                        RecipeServiceSub.getTipsByStatusCopy(recipe.getStatus(), recipe, effective, (orderStatus == null || 0 >= orderStatus.size()) ? null : orderStatus.get(recipe.getOrderCode()));

                recipe.setShowTip(MapValueUtil.getString(tipMap, "listTips"));
                recipeMap.put(recipe.getRecipeId(), convertRecipeForRAP(recipe));
            }

            Map<String, PatientVO> patientMap = Maps.newHashMap();
            if (CollectionUtils.isNotEmpty(patientIds)) {
                List<PatientDTO> patientList = patientService.findByMpiIdIn(patientIds);
                if (CollectionUtils.isNotEmpty(patientList)) {
                    for (PatientDTO patient : patientList) {
                        //设置患者数据
                        RecipeServiceSub.setPatientMoreInfo(patient, doctorId);
                        patientMap.put(patient.getMpiId(), convertSensitivePatientForRAP(patient));
                    }
                }
            }

            for (Recipe recipe : recipeList) {
                String mpiId = recipe.getMpiid();
                HashMap<String, Object> map = Maps.newHashMap();
                map.put("recipe", recipeMap.get(recipe.getRecipeId()));
                map.put("patient", patientMap.get(mpiId));
                list.add(map);
            }
        }

        return list;
    }

    /**
     * 健康端获取待处理中最新的一单处方单
     *
     * @param mpiId 患者ID
     * @return
     */
    @RpcService
    public Map<String, Object> getLastestPendingRecipe(String mpiId) {
        Assert.hasLength(mpiId, "getLastestPendingRecipe mpiId is null.");
        checkUserHasPermissionByMpiId(mpiId);
        HashMap<String, Object> map = Maps.newHashMap();
        RecipeService recipeService = ApplicationUtils.getRecipeService(RecipeService.class);
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        RecipeCacheService cacheService = ApplicationUtils.getRecipeService(RecipeCacheService.class);

        List<String> allMpiIds = recipeService.getAllMemberPatientsByCurrentPatient(mpiId);
        List<Integer> recipeIds = recipeDAO.findPendingRecipes(allMpiIds, RecipeStatusConstant.CHECK_PASS, 0, 1);
        String title;
        String recipeGetModeTip = "";
        //默认需要展示 “购药”
        map.put("checkEnterprise", true);
        if (CollectionUtils.isNotEmpty(recipeIds)) {
            title = "赶快结算您的处方单吧！";
            List<Map> recipesMap = new ArrayList<>(0);
            for (Integer recipeId : recipeIds) {
                Map<String, Object> recipeInfo = recipeService.getPatientRecipeById(recipeId);
                recipeGetModeTip = MapValueUtil.getString(recipeInfo, "recipeGetModeTip");
                if (null != recipeInfo.get("checkEnterprise")) {
                    map.put("checkEnterprise", (Boolean) recipeInfo.get("checkEnterprise"));
                }
                recipesMap.add(recipeInfo);
            }
            map.put("recipes", recipesMap);
        } else {
            title = "暂无待处理处方单";
        }

        List<PatientRecipeDS> otherRecipes = this.findOtherRecipesForPatient(mpiId, 0, 1);
        if (CollectionUtils.isNotEmpty(otherRecipes)) {
            map.put("haveFinished", true);
        } else {
            map.put("haveFinished", false);
        }

        map.put("title", title);
        map.put("unSendTitle", cacheService.getParam(ParameterConstant.KEY_RECIPE_UNSEND_TIP));
        map.put("recipeGetModeTip", recipeGetModeTip);

        return map;
    }

    @RpcService
    public List<PatientRecipeDS> findOtherRecipesForPatient(String mpiId, Integer index, Integer limit) {
        Assert.hasLength(mpiId, "findOtherRecipesForPatient mpiId is null.");
        checkUserHasPermissionByMpiId(mpiId);
        RecipeService recipeService = ApplicationUtils.getRecipeService(RecipeService.class);
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);

        List<String> allMpiIds = recipeService.getAllMemberPatientsByCurrentPatient(mpiId);
        //获取待处理那边最新的一单
        List<Integer> recipeIds = recipeDAO.findPendingRecipes(allMpiIds, RecipeStatusConstant.CHECK_PASS, 0, 1);
        List<PatientRecipeBean> backList = recipeDAO.findOtherRecipesForPatient(allMpiIds, recipeIds, index, limit);

        return ObjectCopyUtils.convert(processListDate(backList, allMpiIds), PatientRecipeDS.class);
    }

    /**
     * 获取所有处方单信息
     * 患者端没有用到
     * @param mpiId
     * @param index
     * @param limit
     * @return
     */
    @RpcService
    public List<PatientRecipeDTO> findAllRecipesForPatient(String mpiId, Integer index, Integer limit) {
        Assert.hasLength(mpiId, "findAllRecipesForPatient mpiId is null.");
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);

//        List<String> allMpiIds = recipeService.getAllMemberPatientsByCurrentPatient(mpiId);
        List<String> allMpiIds = Arrays.asList(mpiId);
        //获取待处理那边最新的一单
//        List<Integer> recipeIds = recipeDAO.findPendingRecipes(allMpiIds, RecipeStatusConstant.CHECK_PASS,0,1);
        List<PatientRecipeBean> backList = recipeDAO.findOtherRecipesForPatient(allMpiIds, null, index, limit);
        return processListDate(backList, allMpiIds);
    }

    /**
     * 处理列表数据
     *
     * @param list
     * @param allMpiIds
     */
    private List<PatientRecipeDTO> processListDate(List<PatientRecipeBean> list, List<String> allMpiIds) {
        PatientService patientService = ApplicationUtils.getBasicService(PatientService.class);
        DrugsEnterpriseService drugsEnterpriseService = ApplicationUtils.getRecipeService(DrugsEnterpriseService.class);
        List<PatientRecipeDTO> backList = Lists.newArrayList();
        if (CollectionUtils.isNotEmpty(list)) {
            backList = ObjectCopyUtils.convert(list, PatientRecipeDTO.class);
            //处理订单类型数据
            RecipeDetailDAO detailDAO = DAOFactory.getDAO(RecipeDetailDAO.class);
            List<PatientDTO> patientList = patientService.findByMpiIdIn(allMpiIds);
            Map<String, PatientDTO> patientMap = Maps.newHashMap();
            if (null != patientList && !patientList.isEmpty()) {
                for (PatientDTO p : patientList) {
                    if (StringUtils.isNotEmpty(p.getMpiId())) {
                        patientMap.put(p.getMpiId(), p);
                    }
                }
            }

            Map<Integer, Boolean> checkEnterprise = Maps.newHashMap();
            PatientDTO p;
            for (PatientRecipeDTO record : backList) {
                p = patientMap.get(record.getMpiId());
                if (null != p) {
                    record.setPatientName(p.getPatientName());
                    record.setPhoto(p.getPhoto());
                    record.setPatientSex(p.getPatientSex());
                }
                //能否购药进行设置，默认可购药
                record.setCheckEnterprise(true);
                if (null != record.getOrganId()) {
                    if (null == checkEnterprise.get(record.getOrganId())) {
                        checkEnterprise.put(record.getOrganId(),
                                drugsEnterpriseService.checkEnterprise(record.getOrganId()));
                    }
                    record.setCheckEnterprise(checkEnterprise.get(record.getOrganId()));
                }

                if (LIST_TYPE_RECIPE.equals(record.getRecordType())) {
                    record.setStatusText(getRecipeStatusText(record.getStatusCode()));
                    //设置失效时间
                    if (RecipeStatusConstant.CHECK_PASS == record.getStatusCode()) {
                        record.setRecipeSurplusHours(RecipeServiceSub.getRecipeSurplusHours(record.getSignDate()));
                    }
                    //药品详情
                    List<Recipedetail> recipedetailList = detailDAO.findByRecipeId(record.getRecordId());
                    record.setRecipeDetail(ObjectCopyUtils.convert(recipedetailList, RecipeDetailBean.class));
                } else if (LIST_TYPE_ORDER.equals(record.getRecordType())) {
                    RecipeOrderService orderService = ApplicationUtils.getRecipeService(RecipeOrderService.class);
                    record.setStatusText(getOrderStatusText(record.getStatusCode()));
                    RecipeResultBean resultBean = orderService.getOrderDetailById(record.getRecordId());
                    if (RecipeResultBean.SUCCESS.equals(resultBean.getCode())) {
                        if (null != resultBean.getObject() && resultBean.getObject() instanceof RecipeOrderBean) {
                            RecipeOrderBean order = (RecipeOrderBean) resultBean.getObject();
                            if (null != order.getLogisticsCompany()) {
                                try {
                                    //4.01需求：物流信息查询
                                    String logComStr = DictionaryController.instance().get("eh.cdr.dictionary.KuaiDiNiaoCode")
                                            .getText(order.getLogisticsCompany());
                                    record.setLogisticsCompany(logComStr);
                                    record.setTrackingNumber(order.getTrackingNumber());
                                } catch (ControllerException e) {
                                    LOGGER.warn("processListDate KuaiDiNiaoCode get error. code={}", order.getLogisticsCompany(),e);
                                }
                            }
                            List<PatientRecipeDTO> recipeList = (List<PatientRecipeDTO>) order.getList();
                            if (CollectionUtils.isNotEmpty(recipeList)) {
                                // 前端要求，先去掉数组形式，否则前端不好处理
//                                List<PatientRecipeBean> subList = new ArrayList<>(5);
//                                PatientRecipeBean _bean;
                                for (PatientRecipeDTO recipe : recipeList) {
//                                    _bean = new PatientRecipeBean();
//                                    _bean.setRecordType(LIST_TYPE_RECIPE);
                                    // 当前订单只有一个处方，处方内的患者信息使用订单的信息就可以
//                                    _bean.setPatientName(record.getPatientName());
//                                    _bean.setPhoto(record.getPhoto());
//                                    _bean.setPatientSex(record.getPatientSex());

                                    record.setRecipeId(recipe.getRecipeId());
                                    record.setRecipeType(recipe.getRecipeType());
                                    record.setOrganDiseaseName(recipe.getOrganDiseaseName());
                                    record.setRecipeMode(recipe.getRecipeMode());
                                    // 订单支付方式
                                    record.setPayMode(recipe.getPayMode());
                                    //药品详情
                                    record.setRecipeDetail(recipe.getRecipeDetail());
//                                    _bean.setSignDate(recipe.getSignDate());
                                    if (RecipeStatusConstant.CHECK_PASS == recipe.getStatusCode()
                                            && OrderStatusConstant.READY_PAY.equals(record.getStatusCode())) {
                                        record.setRecipeSurplusHours(recipe.getRecipeSurplusHours());
                                    }
//                                    subList.add(_bean);
                                }

//                                record.setRecipeList(subList);
                            }
                        }
                    }
                }
            }
        }

        return backList;
    }

    /**
     * 获取最新开具的处方单前limit条，用于跑马灯显示
     *
     * @param limit
     * @return
     */
    public List<RecipeRollingInfoBean> findLastesRecipeList(List<Integer> organIds, int start, int limit) {
        IPatientService iPatientService = ApplicationUtils.getBaseService(IPatientService.class);
        //date  2019/12/16
        //修改findTestDoctors接口从basic查询
        DoctorService iDoctorService = ApplicationUtils.getBasicService(DoctorService.class);
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);

        List<Integer> testDocIds = iDoctorService.findTestDoctors(organIds);
        String endDt = DateTime.now().toString(DateConversion.DEFAULT_DATE_TIME);
        String startDt = DateTime.now().minusMonths(3).toString(DateConversion.DEFAULT_DATE_TIME);
        List<RecipeRollingInfo> list = recipeDAO.findLastesRecipeList(startDt, endDt, organIds, testDocIds, start, limit);

        // 个性化微信号医院没有开方医生不展示
        if (CollectionUtils.isEmpty(list)) {
            return Lists.newArrayList();
        }
        List<String> mpiIdList = new ArrayList<>();
        List<RecipeRollingInfoBean> backList = Lists.newArrayList();
        RecipeRollingInfoBean bean;
        for (RecipeRollingInfo info : list) {
            mpiIdList.add(info.getMpiId());
            bean = new RecipeRollingInfoBean();
            BeanUtils.copyProperties(info, bean);
            backList.add(bean);
        }

        List<PatientBean> patientList = iPatientService.findByMpiIdIn(mpiIdList);
        Map<String, PatientBean> patientMap = Maps.uniqueIndex(patientList, new Function<PatientBean, String>() {
            @Override
            public String apply(PatientBean input) {
                return input.getMpiId();
            }
        });

        PatientBean patient;
        for (RecipeRollingInfoBean info : backList) {
            patient = patientMap.get(info.getMpiId());
            if (null != patient) {
                info.setPatientName(patient.getPatientName());
            }
        }

        return backList;
    }

    /**
     * 处方患者端主页展示推荐医生 (样本采集数量在3个月内)
     *
     * @param start
     * @param limit
     * @return
     */
    @RpcService
    public List<Integer> findDoctorIdSortByCount(int start, int limit, List<Integer> organIds) {
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        //date  2019/12/16
        //修改findTestDoctors接口从basic查询
        DoctorService iDoctorService = ApplicationUtils.getBasicService(DoctorService.class);

        List<Integer> testDocIds = iDoctorService.findTestDoctors(organIds);
        String endDt = DateTime.now().toString(DateConversion.DEFAULT_DATE_TIME);
        String startDt = DateTime.now().minusMonths(3).toString(DateConversion.DEFAULT_DATE_TIME);
        return recipeDAO.findDoctorIdSortByCount(startDt, endDt, organIds, testDocIds, start, limit);
    }

    private String getOrderStatusText(Integer status) {
        String msg = "未知";
        if (OrderStatusConstant.FINISH.equals(status)) {
            msg = "已完成";
        } else if (OrderStatusConstant.READY_PAY.equals(status)) {
            msg = "待支付";
        } else if (OrderStatusConstant.READY_GET_DRUG.equals(status)) {
            msg = "待取药";
        } else if (OrderStatusConstant.READY_CHECK.equals(status)) {
            msg = "待审核";
        } else if (OrderStatusConstant.READY_SEND.equals(status)) {
            msg = "待配送";
        } else if (OrderStatusConstant.SENDING.equals(status)) {
            msg = "配送中";
        } else if (OrderStatusConstant.CANCEL_NOT_PASS.equals(status)) {
            msg = "已取消，审核未通过";
        } else if (OrderStatusConstant.CANCEL_AUTO.equals(status)
                || OrderStatusConstant.CANCEL_MANUAL.equals(status)) {
            msg = "已取消";
        }

        return msg;
    }

    private String getRecipeStatusText(int status) {
        String msg;
        switch (status) {
            case RecipeStatusConstant.FINISH:
                msg = "已完成";
                break;
            case RecipeStatusConstant.HAVE_PAY:
                msg = "已支付，待取药";
                break;
            case RecipeStatusConstant.CHECK_PASS:
                msg = "待处理";
                break;
            case RecipeStatusConstant.NO_PAY:
            case RecipeStatusConstant.NO_OPERATOR:
            case RecipeStatusConstant.REVOKE:
            case RecipeStatusConstant.NO_DRUG:
            case RecipeStatusConstant.CHECK_NOT_PASS_YS:
            case RecipeStatusConstant.DELETE:
            case RecipeStatusConstant.HIS_FAIL:
                msg = "已取消";
                break;
            case RecipeStatusConstant.IN_SEND:
                msg = "配送中";
                break;
            case RecipeStatusConstant.WAIT_SEND:
            case RecipeStatusConstant.READY_CHECK_YS:
            case RecipeStatusConstant.CHECK_PASS_YS:
                msg = "待配送";
                break;
            case RecipeStatusConstant.USING:
                msg = "处理中";
                break;
            default:
                msg = "未知状态";
        }

        return msg;
    }

    private String getOrderStatusTabText(Integer status, Integer giveMode) {
        String msg = "未知";
        if (OrderStatusConstant.FINISH.equals(status)) {
            msg = "已完成";
        } else if (OrderStatusConstant.READY_PAY.equals(status)) {
            msg = "待支付";
        } else if (OrderStatusConstant.READY_GET_DRUG.equals(status) || OrderStatusConstant.NO_DRUG.equals(status) || OrderStatusConstant.HAS_DRUG.equals(status)) {
            msg = "待取药";
            if(OrderStatusConstant.READY_GET_DRUG.equals(status) && null != giveMode && RecipeBussConstant.GIVEMODE_DOWNLOAD_RECIPE.equals(giveMode)){
                msg = "待下载";
            }
        } else if (OrderStatusConstant.READY_CHECK.equals(status)) {
            msg = "待审核";
        } else if (OrderStatusConstant.READY_SEND.equals(status)) {
            msg = "待配送";
        } else if (OrderStatusConstant.SENDING.equals(status)) {
            msg = "配送中";
        } else if (OrderStatusConstant.CANCEL_NOT_PASS.equals(status)) {
            msg = "审核不通过";
        } else if (OrderStatusConstant.CANCEL_AUTO.equals(status)
                || OrderStatusConstant.CANCEL_MANUAL.equals(status)) {
            msg = "已取消";
        }else if (OrderStatusConstant.READY_DRUG.equals(status)){
            msg = "准备中";
        }

        return msg;
    }

    private String getRecipeStatusTabText(int status, int recipeId) {
        String msg;
        switch (status) {
            case RecipeStatusConstant.FINISH:
                msg = "已完成";
                break;
            case RecipeStatusConstant.HAVE_PAY:
                msg = "已支付，待取药";
                break;
            case RecipeStatusConstant.CHECK_PASS:
                msg = "待处理";
                break;
            case RecipeStatusConstant.NO_PAY:
                msg = "未支付";
                break;
            case RecipeStatusConstant.NO_OPERATOR:
                msg = "未处理";
                break;
            //已撤销从已取消拆出来
            case RecipeStatusConstant.REVOKE:
                RecipeRefundDAO recipeRefundDAO = DAOFactory.getDAO(RecipeRefundDAO.class);
                if(CollectionUtils.isNotEmpty(recipeRefundDAO.findRefundListByRecipeId(recipeId))){
                    msg = "已取消";
                }else{

                    msg = "已撤销";
                }

                break;
            //已撤销从已取消拆出来
            case RecipeStatusConstant.DELETE:
                msg = "已删除";
                break;
            //写入his失败从已取消拆出来
            case RecipeStatusConstant.HIS_FAIL:
                msg = "写入his失败";
                break;
            case RecipeStatusConstant.CHECK_NOT_PASS_YS:
                msg = "审核不通过";
                break;
            case RecipeStatusConstant.IN_SEND:
                msg = "配送中";
                break;
            case RecipeStatusConstant.WAIT_SEND:
                msg = "待配送";
                break;
            case RecipeStatusConstant.READY_CHECK_YS:
                msg = "待审核";
                break;
            case RecipeStatusConstant.CHECK_PASS_YS:
                msg = "审核通过";
                break;
            //这里患者取药失败和取药失败都判定为失败
            case RecipeStatusConstant.NO_DRUG:
            case RecipeStatusConstant.RECIPE_FAIL:
                msg = "失败";
                break;
            case RecipeStatusConstant.RECIPE_DOWNLOADED:
                msg = "待取药";
                break;
            case RecipeStatusConstant.USING:
                msg = "处理中";
                break;
            //date 20200511
            //药师签名状态依旧是待审核标识
            case RecipeStatusConstant.SIGN_ERROR_CODE_PHA:
                msg = "待审核";
                break;
            case RecipeStatusConstant.SIGN_ING_CODE_PHA:
                msg = "待审核";
                break;
            default:
                msg = "未知状态";
        }

        return msg;
    }


    /**
     * 获取历史处方
     * @param consultId
     * @param organId
     * @param doctorId
     * @param mpiId
     * @return
     */
    @RpcService
    public List<Map<String, Object>> findHistoryRecipeList(Integer consultId,Integer organId,Integer doctorId, String mpiId) {
        LOGGER.info("findHistoryRecipeList consultId={}, organId={},doctorId={},mpiId={}", consultId, organId,doctorId,mpiId);

        //从his获取线下处方
        RecipePreserveService recipeService = ApplicationUtils.getRecipeService(RecipePreserveService.class);
        Future<Map<String, Object>> hisTask = GlobalEventExecFactory.instance().getExecutor().submit(()->{
            return recipeService.getHosRecipeList(consultId, organId, mpiId, 180);
        });
        //从Recipe表获取线上、线下处方
        List<Map<String,Object>> onLineAndUnderLineRecipesByRecipe=new ArrayList<>();
        try{
            onLineAndUnderLineRecipesByRecipe=findRecipeListByDoctorAndPatient(doctorId,mpiId,0,10000);
            LOGGER.info("findHistoryRecipeList 从recipe表获取处方信息success:{}",onLineAndUnderLineRecipesByRecipe);
        }catch (Exception e){
            LOGGER.info("findHistoryRecipeList 从recipe表获取处方信息error:{}",e);
        }

        Map<String,Object> upderLineRecipesByHis= new ConcurrentHashMap<>();
        try {
            upderLineRecipesByHis = hisTask.get(5000, TimeUnit.MILLISECONDS);
            LOGGER.info("findHistoryRecipeList 从his获取已缴费处方信息:{}",upderLineRecipesByHis);
        } catch (Exception e) {
            e.printStackTrace();
            LOGGER.error("findHistoryRecipeList hisTask exception:{}",e.getMessage(),e);
        }

        //过滤重复数据并重新排序
        List<Map<String,Object>> res=dealRepeatDataAndSort(onLineAndUnderLineRecipesByRecipe,upderLineRecipesByHis);
        //返回结果集
        return res;
    }

    /**
     * 重复数据处理、数据重新排序
     * @param onLineAndUnderLineRecipesByRecipe
     * @param upderLineRecipesByHis
     * @return
     */
    private List<Map<String, Object>> dealRepeatDataAndSort(List<Map<String, Object>> onLineAndUnderLineRecipesByRecipe, Map<String, Object> upderLineRecipesByHis) {
        LOGGER.info("dealRepeatDataAndSort参数onLineAndUnderLineRecipesByRecipe:{},upderLineRecipesByHis:{}",JSONUtils.toString(upderLineRecipesByHis),JSONUtils.toString(upderLineRecipesByHis));
        List<Map<String, Object>> res=new ArrayList<>();
        //过滤重复数据
        List<HisRecipeBean> hisRecipes=(List<HisRecipeBean>)upderLineRecipesByHis.get("hisRecipe");
        if(hisRecipes==null||hisRecipes.size()<=0) return onLineAndUnderLineRecipesByRecipe;
        if(onLineAndUnderLineRecipesByRecipe!=null&&onLineAndUnderLineRecipesByRecipe.size()>0){
            for( Map<String,Object> map:onLineAndUnderLineRecipesByRecipe){
                RecipeBean recipeBean=(RecipeBean)map.get("recipe");
                String recipeKey=recipeBean.getRecipeCode()+recipeBean.getClinicOrgan();
                for (int i = hisRecipes.size() - 1; i >= 0; i--) {
                    HisRecipeBean hisRecipeBean=hisRecipes.get(i);
                    String hiskey=hisRecipeBean.getRecipeCode()+hisRecipeBean.getClinicOrgan();
                    if(StringUtils.isEmpty(hiskey)) continue;
                    if(hiskey.equals(recipeKey)){
                        LOGGER.info("dealRepeatDataAndSort删除线下处方:recipeCode{},clinicOrgan{}",hisRecipeBean.getRecipeCode(),hisRecipeBean.getClinicOrgan());
                        hisRecipes.remove(hisRecipeBean);//删除重复元素
                    }
                }
            }
        }

        //合并数据
        res.addAll(onLineAndUnderLineRecipesByRecipe);
        for (int i = hisRecipes.size() - 1; i >= 0; i--) {
            HisRecipeBean hisRecipeBean=hisRecipes.get(i);
            Map<String,Object> map=new HashMap<>();
            map.put("recipe", RecipeServiceSub.convertHisRecipeForRAP(hisRecipeBean));
            map.put("patient", upderLineRecipesByHis.get("patient"));
            res.add(map);
        }

        //根据创建时间降序排序
        Collections.sort(res, new Comparator<Map<String, Object>>() {
            public int compare(Map<String, Object> o1, Map<String, Object> o2) {
                Date date1 = ((RecipeBean)o1.get("recipe")).getCreateDate() ;
                Date date2 = ((RecipeBean)o2.get("recipe")).getCreateDate() ;
                return date2.compareTo(date1);
            }
        });

        return res;
    }

    /**
     * 查找指定医生和患者间开的处方单列表
     *
     * @param doctorId
     * @param mpiId
     * @param start
     * @param limit
     * @return
     */
    @RpcService
    public List<Map<String, Object>> findRecipeListByDoctorAndPatient(Integer doctorId, String mpiId, int start, int limit) {
        checkUserHasPermissionByDoctorId(doctorId);
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        PatientService patientService = ApplicationUtils.getBasicService(PatientService.class);
        //List<Recipe> recipes = recipeDAO.findRecipeListByDoctorAndPatient(doctorId, mpiId, start, limit);
        //修改逻辑历史处方中获取的处方列表：只显示未处理、未支付、审核不通过、失败、已完成状态的
        List<Recipe> recipes = recipeDAO.findRecipeListByDoctorAndPatientAndStatusList(doctorId, mpiId, start, limit, new ArrayList<>(Arrays.asList(HistoryRecipeListShowStatusList)));
        LOGGER.info("findRecipeListByDoctorAndPatient mpiId:{} ,recipes:{} ",mpiId,JSONUtils.toString(recipes));
        PatientVO patient = RecipeServiceSub.convertSensitivePatientForRAP(patientService.get(mpiId));
        LOGGER.info("findRecipeListByDoctorAndPatient mpiId:{} ,patient:{} ",mpiId,JSONUtils.toString(patient));
        return instanceRecipesAndPatient(recipes,patient);
    }

    /**
     *  获取返回对象
     * @param recipes
     * @param patient
     * @return
     */
    public List<Map<String, Object>> instanceRecipesAndPatient(List<Recipe> recipes,PatientVO patient) {
        LOGGER.info("instanceRecipesAndPatient recipes:{} ,patient:{} ",JSONUtils.toString(recipes),JSONUtils.toString(patient));
        List<Map<String, Object>> list = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(recipes)) {
            RecipeOrderDAO orderDAO = DAOFactory.getDAO(RecipeOrderDAO.class);
            RecipeDetailDAO recipeDetailDAO = DAOFactory.getDAO(RecipeDetailDAO.class);
            OrganDrugListDAO organDrugListDAO = DAOFactory.getDAO(OrganDrugListDAO.class);

            //date 20200506
            //获取处方对应的订单信息
            Map<String, Integer> orderStatus = new HashMap<>();
            List<String> recipeCodes = recipes.stream().map(recipe -> recipe.getOrderCode()).filter(code -> StringUtils.isNotEmpty(code)).collect(Collectors.toList());
            if(CollectionUtils.isNotEmpty(recipeCodes)){
                List<RecipeOrder> recipeOrders = orderDAO.findValidListbyCodes(recipeCodes);
                orderStatus = recipeOrders.stream().collect(Collectors.toMap(RecipeOrder::getOrderCode, RecipeOrder::getStatus));
            }

            for (Recipe recipe : recipes) {
                Map<String, Object> map = Maps.newHashMap();
                //设置处方具体药品名称
                List<Recipedetail> recipedetails = recipeDetailDAO.findByRecipeId(recipe.getRecipeId());
                LOGGER.info("instanceRecipesAndPatient recipeid:{} ,recipedetails:{} ",recipe.getRecipeId(),JSONUtils.toString(recipedetails));
                StringBuilder stringBuilder = new StringBuilder();

                for (Recipedetail recipedetail : recipedetails) {
                    List<OrganDrugList> organDrugLists = organDrugListDAO.findByDrugIdAndOrganId(recipedetail.getDrugId(), recipe.getClinicOrgan());
                    if (organDrugLists != null&&organDrugLists.size()>0) {
                        stringBuilder.append(organDrugLists.get(0).getSaleName());
                        if (StringUtils.isNotEmpty(organDrugLists.get(0).getDrugForm())) {
                            stringBuilder.append(organDrugLists.get(0).getDrugForm());
                        }
                    } else {
                        stringBuilder.append(recipedetail.getDrugName());
                    }
                    stringBuilder.append(" ").append(recipedetail.getDrugSpec()).append("/").append(recipedetail.getDrugUnit()).append("、");
                }
                stringBuilder.deleteCharAt(stringBuilder.lastIndexOf("、"));
                recipe.setRecipeDrugName(stringBuilder.toString());
                recipe.setRecipeShowTime(recipe.getCreateDate());
                boolean effective = false;
                //只有审核未通过的情况需要看订单状态
                if (RecipeStatusConstant.CHECK_NOT_PASS_YS == recipe.getStatus()) {
                    effective = orderDAO.isEffectiveOrder(recipe.getOrderCode(), recipe.getPayMode());
                }
                //Map<String, String> tipMap = RecipeServiceSub.getTipsByStatus(recipe.getStatus(), recipe, effective);
                //date 20190929
                //修改医生端状态文案显示
                //date 20200506
                //添加订单的状态
                Map<String, String> tipMap = RecipeServiceSub.getTipsByStatusCopy(recipe.getStatus(), recipe, effective, (orderStatus == null || 0 >= orderStatus.size()) ? null : orderStatus.get(recipe.getOrderCode()));

                recipe.setShowTip(MapValueUtil.getString(tipMap, "listTips"));
                map.put("recipe", RecipeServiceSub.convertRecipeForRAP(recipe));
                map.put("patient", patient);
                list.add(map);
            }

        }
        return list;
    }

    /**
     * 获取医生开过处方的历史患者列表
     *
     * @param doctorId
     * @param start
     * @return
     */
//    @RpcService
//    public List<PatientDTO> findHistoryPatientsFromRecipeByDoctor(Integer doctorId, int start, int limit) {
//        checkUserHasPermissionByDoctorId(doctorId);
//        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
//        final List<String> mpiList = recipeDAO.findHistoryMpiIdsByDoctorId(doctorId, start, limit);
//        if (mpiList.size() == 0) {
//            return new ArrayList<>();
//        }
//        PatientService patientService = ApplicationUtils.getBasicService(pa.class);
//        return patientService.getPatients(mpiList, doctorId);
//    }

    /**
     * 获取患者的所有处方单-web福建省立
     *
     * @param mpiId
     * @param start
     * @return
     */
    @RpcService
    public Map<String,Object> findAllRecipesForPatient(String mpiId, Integer organId, int start, int limit) {
        LOGGER.info("findAllRecipesForPatient mpiId ="+mpiId);
        Map<String,Object> result = Maps.newHashMap();
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        RecipeDetailDAO detailDAO = DAOFactory.getDAO(RecipeDetailDAO.class);
        QueryResult<Recipe> resultList = recipeDAO.findRecipeListByMpiID(mpiId,organId, start, limit);
        List<Recipe> list = resultList.getItems();
        if (CollectionUtils.isEmpty(list)){
            return result;
        }
        result.put("total",resultList.getTotal());
        result.put("start",resultList.getStart());
        result.put("limit",resultList.getLimit());
        List<Map<String,Object>> mapList = Lists.newArrayList();
        Map<String,Object> map;
        List<Recipedetail> recipedetails;
        try{
            Dictionary usingRateDic = DictionaryController.instance().get("eh.cdr.dictionary.UsingRate");
            Dictionary usePathwaysDic = DictionaryController.instance().get("eh.cdr.dictionary.UsePathways");
            Dictionary departDic = DictionaryController.instance().get("eh.base.dictionary.Depart");
            String organText = DictionaryController.instance().get("eh.base.dictionary.Organ").getText(organId);
            for (Recipe recipe : list){
                map = Maps.newHashMap();
                map.put("recipeId",recipe.getRecipeId());
                map.put("patientName",recipe.getPatientName());
                map.put("doctorDepart",organText+departDic.getText(recipe.getDepart()));
                map.put("diseaseName",recipe.getOrganDiseaseName());
                map.put("signTime",DateConversion.getDateFormatter(recipe.getSignDate(), "MM月dd日 HH:mm"));
                map.put("doctorName",recipe.getDoctorName());
                recipedetails = detailDAO.findByRecipeId(recipe.getRecipeId());

                Map<String,String> drugInfo;
                List<Map<String,String>> drugInfoList = Lists.newArrayList();
                String useDose;
                String usingRateText;
                String usePathwaysText;
                for (Recipedetail detail : recipedetails){
                    drugInfo = Maps.newHashMap();
                    if (StringUtils.isNotEmpty(detail.getUseDoseStr())){
                        useDose = detail.getUseDoseStr();
                    }else {
                        useDose = detail.getUseDose()==null?null:String.valueOf(detail.getUseDose());
                    }
                    drugInfo.put("drugName",detail.getDrugName());
                    //开药总量+药品单位
                    String dSpec = "*"+detail.getUseTotalDose().intValue() + detail.getDrugUnit();
                    drugInfo.put("drugTotal",dSpec);
                    usingRateText = StringUtils.isNotEmpty(detail.getUsingRateTextFromHis())?detail.getUsingRateTextFromHis():usingRateDic.getText(detail.getUsingRate());
                    usePathwaysText = StringUtils.isNotEmpty(detail.getUsePathwaysTextFromHis())?detail.getUsePathwaysTextFromHis():usePathwaysDic.getText(detail.getUsePathways());
                    String useWay = "用法：每次" + useDose + detail.getUseDoseUnit()
                            +"/"+usingRateText
                            +"/"+usePathwaysText
                            +detail.getUseDays() + "天";
                    drugInfo.put("useWay",useWay);
                    drugInfoList.add(drugInfo);
                }
                map.put("rp", drugInfoList);
                map.put("memo",recipe.getMemo());
                switch (recipe.getStatus()){
                    case RecipeStatusConstant.CHECK_PASS:
                        if (StringUtils.isNotEmpty(recipedetails.get(0).getPharmNo())){
                            map.put("statusText","药师审核处方通过，请去医院取药窗口取药:["+recipedetails.get(0).getPharmNo()+"]");
                        }else {
                            map.put("statusText","药师审核处方通过，请去医院取药窗口取药");
                        }
                        break;
                    case RecipeStatusConstant.NO_DRUG:
                    case RecipeStatusConstant.NO_OPERATOR:
                        map.put("statusText","已取消(超过三天未取药)");
                        break;
                    case RecipeStatusConstant.REVOKE:
                        map.put("statusText","由于医生已撤销，该处方单已失效，请联系医生.");
                        break;
                    case RecipeStatusConstant.FINISH:
                        map.put("statusText","已完成");
                        break;
                    case RecipeStatusConstant.READY_CHECK_YS:
                        map.put("statusText","等待药师审核处方");
                        break;
                    case RecipeStatusConstant.CHECK_NOT_PASS_YS:
                        map.put("statusText","药师审核处方不通过，请联系开方医生");
                        break;
                    default:
                        map.put("statusText",DictionaryController.instance().get("eh.cdr.dictionary.RecipeStatus").getText(recipe.getStatus()));
                        break;
                }
                mapList.add(map);
            }
            result.put("list",mapList);
        }catch (Exception e){
            LOGGER.error("findAllRecipesForPatient error"+e.getMessage(),e);
        }

        return result;
    }

    @RpcService
    public List<PatientTabStatusRecipeDTO> findRecipesForPatientAndTabStatus(String tabStatus, String mpiId, Integer index, Integer limit) {
        LOGGER.info("findRecipesForPatientAndTabStatus tabStatus:{} mpiId:{} ", tabStatus, mpiId);
        List<PatientTabStatusRecipeDTO> recipeList = new ArrayList<>();
        Assert.hasLength(mpiId, "findRecipesForPatientAndTabStatus 用户id为空!");
        checkUserHasPermissionByMpiId(mpiId);
        RecipeService recipeService = ApplicationUtils.getRecipeService(RecipeService.class);
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);

        List<String> allMpiIds = recipeService.getAllMemberPatientsByCurrentPatient(mpiId);
        //获取页面展示的对象
        TabStatusEnum recipeStatusList = TabStatusEnum.fromTabStatusAndStatusType(tabStatus, "recipe");
        if(null == recipeStatusList){
            LOGGER.error("findRecipesForPatientAndTabStatus:{}tab没有查询到recipe的状态列表", tabStatus);
            return recipeList;
        }
        TabStatusEnum orderStatusList = TabStatusEnum.fromTabStatusAndStatusType(tabStatus, "order");
        if(null == orderStatusList){
            LOGGER.error("findRecipesForPatientAndTabStatus:{}tab没有查询到order的状态列表", tabStatus);
            return recipeList;
        }
        List<Integer> specialStatusList = new ArrayList<>();
        if("ongoing".equals(tabStatus)){
            specialStatusList.add(RecipeStatusConstant.RECIPE_DOWNLOADED);
            //date 20200511
            //添加处方单中新加的药师签名中签名失败的，患者认为是待审核
            specialStatusList.addAll(new ArrayList<Integer>(){
                {add(RecipeStatusConstant.SIGN_ERROR_CODE_PHA);
                    add(RecipeStatusConstant.SIGN_ING_CODE_PHA);}
            });
        }
        try{
            List<PatientRecipeBean> backList = recipeDAO.findTabStatusRecipesForPatient(allMpiIds, index, limit, recipeStatusList.getStatusList(), orderStatusList.getStatusList(), specialStatusList, tabStatus);
            return processTabListDate(backList, allMpiIds);
        }catch(Exception e){
            LOGGER.error("findRecipesForPatientAndTabStatus error :.",e);
        }
        return null;
    }

    /**
     * 处理tab下的列表数据
     *
     * @param list
     * @param allMpiIds
     */
    private List<PatientTabStatusRecipeDTO> processTabListDate(List<PatientRecipeBean> list, List<String> allMpiIds) {
        PatientService patientService = ApplicationUtils.getBasicService(PatientService.class);
        DrugsEnterpriseService drugsEnterpriseService = ApplicationUtils.getRecipeService(DrugsEnterpriseService.class);
        List<PatientTabStatusRecipeDTO> backList = Lists.newArrayList();
        OrganDrugListDAO organDrugListDAO = DAOFactory.getDAO(OrganDrugListDAO.class);
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        if (CollectionUtils.isNotEmpty(list)) {
            backList = ObjectCopyUtils.convert(list, PatientTabStatusRecipeDTO.class);
            //处理订单类型数据
            RecipeDetailDAO detailDAO = DAOFactory.getDAO(RecipeDetailDAO.class);
            List<PatientDTO> patientList = patientService.findByMpiIdIn(allMpiIds);
            Map<String, PatientDTO> patientMap = Maps.newHashMap();
            if (null != patientList && !patientList.isEmpty()) {
                for (PatientDTO p : patientList) {
                    if (StringUtils.isNotEmpty(p.getMpiId())) {
                        patientMap.put(p.getMpiId(), p);
                    }
                }
            }

            Map<Integer, Boolean> checkEnterprise = Maps.newHashMap();
            PatientDTO p;
            RecipeOrderService recipeOrderService = ApplicationUtils.getRecipeService(RecipeOrderService.class);
            for (PatientTabStatusRecipeDTO record : backList) {
                p = patientMap.get(record.getMpiId());
                if (null != p) {
                    record.setPatientName(p.getPatientName());
                    record.setPhoto(p.getPhoto());
                    record.setPatientSex(p.getPatientSex());
                }
                /*//获取扁鹊处方流转平台第三方跳转url
                record.setThirdUrl(recipeOrderService.getThirdUrl(record.getRecordId()));*/
                //能否购药进行设置，默认可购药
                record.setCheckEnterprise(true);
                if (null != record.getOrganId()) {
                    if (null == checkEnterprise.get(record.getOrganId())) {
                        checkEnterprise.put(record.getOrganId(),
                                drugsEnterpriseService.checkEnterprise(record.getOrganId()));
                    }
                    record.setCheckEnterprise(checkEnterprise.get(record.getOrganId()));
                }

                if (LIST_TYPE_RECIPE.equals(record.getRecordType())) {
                    record.setStatusText(getRecipeStatusTabText(record.getStatusCode(), record.getRecordId()));
                    //设置失效时间
                    if (RecipeStatusConstant.CHECK_PASS == record.getStatusCode()) {
                        record.setRecipeSurplusHours(RecipeServiceSub.getRecipeSurplusHours(record.getSignDate()));
                    }
                    //药品详情
                    List<Recipedetail> recipedetailList = detailDAO.findByRecipeId(record.getRecordId());

                    for (Recipedetail recipedetail : recipedetailList) {
                        Recipe recipe = recipeDAO.getByRecipeId(recipedetail.getRecipeId());
                        List<OrganDrugList> organDrugLists = organDrugListDAO.findByDrugIdAndOrganId(recipedetail.getDrugId(), recipe.getClinicOrgan());
                        if (CollectionUtils.isNotEmpty(organDrugLists)) {
                            recipedetail.setDrugForm(organDrugLists.get(0).getDrugForm());
                        }
                    }
                    record.setRecipeDetail(ObjectCopyUtils.convert(recipedetailList, RecipeDetailBean.class));
                } else if (LIST_TYPE_ORDER.equals(record.getRecordType())) {
                    RecipeOrderService orderService = ApplicationUtils.getRecipeService(RecipeOrderService.class);
                    record.setStatusText(getOrderStatusTabText(record.getStatusCode(), record.getGiveMode()));
                    RecipeResultBean resultBean = orderService.getOrderDetailById(record.getRecordId());
                    if (RecipeResultBean.SUCCESS.equals(resultBean.getCode())) {
                        if (null != resultBean.getObject() && resultBean.getObject() instanceof RecipeOrderBean) {
                            RecipeOrderBean order = (RecipeOrderBean) resultBean.getObject();
                            record.setEnterpriseId(order.getEnterpriseId());
                            if (null != order.getLogisticsCompany()) {
                                try {
                                    //4.01需求：物流信息查询

                                    String logComStr = DictionaryController.instance().get("eh.cdr.dictionary.KuaiDiNiaoCode")
                                            .getText(order.getLogisticsCompany());
                                    record.setLogisticsCompany(logComStr);
                                    record.setTrackingNumber(order.getTrackingNumber());
                                } catch (ControllerException e) {
                                    LOGGER.warn("findRecipesForPatientAndTabStatus: 获取物流信息失败，物流方code={}", order.getLogisticsCompany(),e);
                                }
                            }
                            List<PatientRecipeDTO> recipeList = (List<PatientRecipeDTO>) order.getList();
                            if (CollectionUtils.isNotEmpty(recipeList)) {
                                for (PatientRecipeDTO recipe : recipeList) {

                                    record.setRecipeId(recipe.getRecipeId());
                                    record.setRecipeType(recipe.getRecipeType());
                                    record.setOrganDiseaseName(recipe.getOrganDiseaseName());
                                    record.setRecipeMode(recipe.getRecipeMode());
                                    // 订单支付方式
                                    record.setPayMode(recipe.getPayMode());
                                    //药品详情
                                    List<RecipeDetailBean> recipedetailList = recipe.getRecipeDetail();
                                    for (RecipeDetailBean recipedetail : recipedetailList) {
                                        Recipe recipes = recipeDAO.getByRecipeId(recipedetail.getRecipeId());
                                        List<OrganDrugList> organDrugLists = organDrugListDAO.findByDrugIdAndOrganId(recipedetail.getDrugId(), recipes.getClinicOrgan());
                                        if (CollectionUtils.isNotEmpty(organDrugLists)) {
                                            recipedetail.setDrugForm(organDrugLists.get(0).getDrugForm());
                                        }
                                    }
                                    record.setRecipeDetail(recipedetailList);
                                    if (RecipeStatusConstant.CHECK_PASS == recipe.getStatusCode()
                                            && OrderStatusConstant.READY_PAY.equals(record.getStatusCode())) {
                                        record.setRecipeSurplusHours(recipe.getRecipeSurplusHours());
                                    }
                                }

                            }
                        }
                    }
                }

                //添加处方笺文件，获取用户处方信息中的处方id，获取处方笺文件,设置跳转的页面
                getPageMsg(record);
                //存入每个页面的按钮信息（展示那种按钮，如果是购药按钮展示哪些按钮）
                PayModeShowButtonBean buttons = getShowButton(record);
                record.setButtons(buttons);
                //根据隐方配置返回处方详情
                boolean isReturnRecipeDetail=isReturnRecipeDetail(record.getRecipeId());
                if(!isReturnRecipeDetail){
                    List<RecipeDetailBean> recipeDetailVOs=record.getRecipeDetail();
                    if(recipeDetailVOs!=null&&recipeDetailVOs.size()>0){
                        for(int j=0;j<recipeDetailVOs.size();j++){
                            recipeDetailVOs.get(j).setDrugName(null);
                            recipeDetailVOs.get(j).setDrugSpec(null);
                        }
                    }
                }
                //返回是否隐方
                record.setIsHiddenRecipeDetail(!isReturnRecipeDetail);
            }
        }

        return backList;
    }

    /**
     * 是否返回处方详情
     * @param
     * @return
     * @author liumin
     */
    public boolean isReturnRecipeDetail(Integer recipeId){
        boolean isReturnRecipeDetail=true;
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        Recipe recipe = recipeDAO.get(recipeId);
        RecipeOrderDAO orderDAO = DAOFactory.getDAO(RecipeOrderDAO.class);
        RecipeOrder order = orderDAO.getOrderByRecipeId(recipe.getRecipeId());
        LOGGER.info("isReturnRecipeDetail recipeId:{} recipe:{} order:{}",recipeId,JSONUtils.toString(recipe),recipeId,JSONUtils.toString(order));
        try{
            //如果运营平台-配置管理 中药是否隐方的配置项, 选择隐方后,患者在支付成功处方费用后才可以显示中药明细，否则就隐藏掉对应的中药明细。
            IConfigurationCenterUtilsService configService = BaseAPI.getService(IConfigurationCenterUtilsService.class);
            Object isHiddenRecipeDetail = configService.getConfiguration(recipe.getClinicOrgan(), "isHiddenRecipeDetail");
            LOGGER.info("isReturnRecipeDetail 是否是中药：{} 是否隐方"
                    ,RecipeUtil.isTcmType(recipe.getRecipeType()),(boolean)isHiddenRecipeDetail);
            if (RecipeUtil.isTcmType(recipe.getRecipeType())//中药
                    &&(boolean)isHiddenRecipeDetail==true//隐方
                    &&(PayConstant.PAY_FLAG_PAY_SUCCESS != recipe.getPayFlag()) //支付状态为非已支付
            ) {
                if(order ==null){
                     if(recipe.getPayMode()==1){//支付方式：线上支付
                         LOGGER.info("isReturnRecipeDetail false recipeId:{} cause:{}",recipeId,"1");
                         return false;
                     }else{
                         if(recipe.getStatus()==6){// 处方状态已完成
                         }else{
                             LOGGER.info("isReturnRecipeDetail false recipeId:{} cause:{}",recipeId,"2");
                             return false;
                         }
                     }
                }else{
                    if(recipe.getPayMode()==1 || "111".equals(order.getWxPayWay())){// 线上支付
                        if((order.getPayFlag()==1)){//返回详情
                        }else{
                            LOGGER.info("isReturnRecipeDetail false recipeId:{} cause:{}",recipeId,"3");
                            isReturnRecipeDetail=false;//不返回详情
                        }
                    }else{//线下支付
                        if(recipe.getStatus()==6){// 处方状态已完成
                        }else{
                            LOGGER.info("isReturnRecipeDetail false recipeId:{} cause:{}",recipeId,"4");
                            return false;
                        }
                    }

                }
            }
        }catch (Exception e){
            LOGGER.error("isReturnRecipeDetail error:{}",e);
        }

        return isReturnRecipeDetail;
    }
    /**
     * @method  getFile
     * @description 获取处方笺文件信息
     * @date: 2019/9/3
     * @author: JRK
     * @param record 患者处方信息
     * @return void
     */
    private void getPageMsg(PatientTabStatusRecipeDTO record) {
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        Recipe recipe = recipeDAO.get(0 == record.getRecipeId() ? record.getRecordId() : record.getRecipeId());
        if(null == recipe){
            LOGGER.warn("processTabListDate: recipeId:{},对应处方信息不存在,", record.getRecipeId());
        }else{
            record.setChemistSignFile(recipe.getChemistSignFile());
            record.setSignFile(recipe.getSignFile());
            record.setJumpPageType(getJumpPage(recipe));
            record.setOrderCode(recipe.getOrderCode());
            record.setClinicOrgan(recipe.getClinicOrgan());
        }
    }

    /**
     * @method  getJumpPage
     * @description 获取跳转的方式
     * @date: 2019/10/8
     * @author: JRK
     * @param recipe 处方信息
     * @return java.lang.Integer
     */
    private Integer getJumpPage(Recipe recipe) {
        Integer jumpPage = RECIPE_PAGE;
        jumpPage = null == recipe.getOrderCode() ? RECIPE_PAGE : ORDER_PAGE;
        return jumpPage;
    }

    /**
     * @method  getShowButton
     * @description 获取页面上展示的按钮信息，按钮展示：true，不展示：false
     * @date: 2019/8/19
     * @author: JRK
     * @param record 获取医院的配置项
     * @return com.ngari.recipe.recipe.model.PayModeShowButtonBean：
     * 医院下购药方式展示的按钮对象
     */
    private PayModeShowButtonBean getShowButton(PatientTabStatusRecipeDTO record) {
        PayModeShowButtonBean payModeShowButtonBean = new PayModeShowButtonBean();

        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        Recipe recipe = recipeDAO.get(0 == record.getRecipeId() ? record.getRecordId() : record.getRecipeId());
        if(null == recipe){
            LOGGER.warn("processTabListDate: recipeId:{},对应处方信息不存在,", record.getRecipeId());
            payModeShowButtonBean.noUserButtons();
            return payModeShowButtonBean;
        }
        //流转到扁鹊处方流转平台的处方购药按钮都不显示
        if (recipe.getEnterpriseId()!=null && RecipeServiceSub.isBQEnterpriseBydepId(recipe.getEnterpriseId())){
            payModeShowButtonBean.noUserButtons();
            return payModeShowButtonBean;
        }
        //获取配置项
        IConfigurationCenterUtilsService configService = BaseAPI.getService(IConfigurationCenterUtilsService.class);

        if(RecipeBussConstant.RECIPEMODE_NGARIHEALTH.equals(record.getRecipeMode())
            || RecipeBussConstant.RECIPEMODE_ZJJGPT.equals(record.getRecipeMode())){

            //添加按钮配置项key
            Object payModeDeploy = configService.getConfiguration(record.getOrganId(), "payModeDeploy");
            if(null == payModeDeploy){
                payModeShowButtonBean.noUserButtons();
                return payModeShowButtonBean;
            }
            List<String> configurations = new ArrayList<>(Arrays.asList((String[])payModeDeploy));
            //将购药方式的显示map对象转化为页面展示的对象
            Map<String, Boolean> buttonMap = new HashMap<>(10);
            for (String configuration : configurations) {
                buttonMap.put(configuration, true);
            }
            payModeShowButtonBean = JSONUtils.parse(JSON.toJSONString(buttonMap), PayModeShowButtonBean.class);
            //当审方为前置并且审核没有通过，设置成不可选择

            //判断购药按钮是否可选状态的,当审方方式是前置且正在审核中时，不可选
            boolean isOptional = !(ReviewTypeConstant.Preposition_Check == recipe.getReviewType() &&
                    (RecipeStatusConstant.SIGN_ERROR_CODE_PHA == recipe.getStatus() || RecipeStatusConstant.SIGN_ING_CODE_PHA == recipe.getStatus() || RecipeStatusConstant.READY_CHECK_YS == recipe.getStatus() || (RecipeStatusConstant.CHECK_NOT_PASS_YS == recipe.getStatus() && RecipecCheckStatusConstant.First_Check_No_Pass == recipe.getCheckStatus())));
            payModeShowButtonBean.setOptional(isOptional);

            //初始化互联网按钮信息（特殊化）
            if(RecipeBussConstant.RECIPEMODE_ZJJGPT.equals(record.getRecipeMode())){
                initInternetModel(record, payModeShowButtonBean, recipe);
            }
        } else{
            LOGGER.warn("processTabListDate: recipeId:{}  recipeMode:{},对应处方流转方式无法识别", record.getRecipeId(), record.getRecipeMode());
            payModeShowButtonBean.noUserButtons();
            return payModeShowButtonBean;
        }

        //设置按钮的展示类型
        Boolean showUseDrugConfig = (Boolean)configService.getConfiguration(record.getOrganId(), "medicationGuideFlag");
        //已完成的处方单设置
        if ((LIST_TYPE_ORDER.equals(record.getRecordType())&& OrderStatusConstant.FINISH.equals(record.getStatusCode()))
                || (LIST_TYPE_RECIPE.equals(record.getRecordType())&& RecipeStatusConstant.FINISH == record.getStatusCode())){
            //设置用药指导按钮
            if (showUseDrugConfig){
                payModeShowButtonBean.setSupportMedicationGuide(true);
            }
        }

        payModeShowButtonBean.setButtonType(getButtonType(payModeShowButtonBean, recipe, record.getRecordType(), record.getStatusCode(), showUseDrugConfig));

        //date 20200508
        //设置展示配送到家的配送方式
        //判断当前处方对应的机构支持的配送药企包含的配送类型

        //首先判断按钮中配送药品购药方式是否展示，不展示购药方式按钮就不展示药企配送和医院配送
        if(!payModeShowButtonBean.getSupportOnline()){
            return payModeShowButtonBean;
        }
        DrugsEnterpriseDAO drugsEnterpriseDAO = DAOFactory.getDAO(DrugsEnterpriseDAO.class);
        List<Integer> payModeSupport = RecipeServiceSub.getDepSupportMode(RecipeBussConstant.PAYMODE_ONLINE);
        payModeSupport.addAll(RecipeServiceSub.getDepSupportMode(RecipeBussConstant.PAYMODE_COD));
        Long enterprisesSend = drugsEnterpriseDAO.getCountByOrganIdAndPayModeSupportAndSendType(recipe.getClinicOrgan(), payModeSupport, EnterpriseSendConstant.Enterprise_Send);
        Long hosSend = drugsEnterpriseDAO.getCountByOrganIdAndPayModeSupportAndSendType(recipe.getClinicOrgan(), payModeSupport, EnterpriseSendConstant.Hos_Send);
        if(null != enterprisesSend && 0 < enterprisesSend){

            payModeShowButtonBean.setShowSendToEnterprises(true);
        }
        if(null != hosSend && 0 < hosSend){

            payModeShowButtonBean.setShowSendToHos(true);
        }
        //不支持配送，则按钮都不显示--包括药店取药
        if (new Integer(2).equals(recipe.getDistributionFlag())){
            payModeShowButtonBean.setShowSendToEnterprises(false);
            payModeShowButtonBean.setShowSendToHos(false);
            payModeShowButtonBean.setSupportTFDS(false);
        }
        return payModeShowButtonBean;
    }

    /**
     * @method  initInternetModel
     * @description 初始化互联网模式的按钮信息
     * @date: 2019/9/3
     * @author: JRK
     * @param record 患者处方信息
     * @param payModeShowButtonBean 按钮信息
     * @param recipe 处方信息
     * @return void
     */
    public void initInternetModel(PatientTabStatusRecipeDTO record, PayModeShowButtonBean payModeShowButtonBean, Recipe recipe) {



        RecipeExtendDAO RecipeExtendDAO = DAOFactory.getDAO(RecipeExtendDAO.class);
        RecipeExtend recipeExtend = RecipeExtendDAO.getByRecipeId(recipe.getRecipeId());
        if(null != recipeExtend && null != recipeExtend.getGiveModeFormHis()){
            if("1".equals(recipeExtend.getGiveModeFormHis())){
                //只支持配送到家
                payModeShowButtonBean.setSupportToHos(false);
            } else if ("2".equals(recipeExtend.getGiveModeFormHis())){
                //只支持到院取药
                payModeShowButtonBean.setSupportOnline(false);
            } else if ("3".equals(recipeExtend.getGiveModeFormHis())){
                //都支持
            } else{
                //都不支持
                payModeShowButtonBean.setSupportOnline(false);
                payModeShowButtonBean.setSupportToHos(false);
            }
        } else {
            //省平台互联网购药方式的配置
            if(1 == recipe.getDistributionFlag()){
                payModeShowButtonBean.setSupportToHos(false);
            }
        }


    }

    /**
     * @method  getButtonType
     * @description 获取按钮显示类型
     * @date: 2019/9/2
     * @author: JRK
     * @param recordType 患者处方的类型
     * @param statusCode 患者处方的状态
     * @return java.lang.Integer 按钮的显示类型
     */
    private Integer getButtonType(PayModeShowButtonBean payModeShowButtonBean, Recipe recipe, String recordType, Integer statusCode, Boolean showUseDrugConfig) {
        //添加判断，当选药按钮都不显示的时候，按钮状态为不展示
        if(null != payModeShowButtonBean){
            //当处方在待处理、前置待审核通过时，购药配送为空不展示按钮
            Boolean noHaveBuyDrugConfig = !payModeShowButtonBean.getSupportOnline() &&
                    !payModeShowButtonBean.getSupportTFDS() && !payModeShowButtonBean.getSupportToHos();

            //只有当亲处方有订单，且物流公司和订单号都有时展示物流信息
            Boolean haveSendInfo = false;
            RecipeOrderDAO orderDAO = DAOFactory.getDAO(RecipeOrderDAO.class);
            RecipeOrder order = orderDAO.getOrderByRecipeId(recipe.getRecipeId());
            if(null != order && null != order.getLogisticsCompany() && StringUtils.isNotEmpty(order.getTrackingNumber())){
                haveSendInfo = true;
            }

            RecipePageButtonStatusEnum buttonStatus = RecipePageButtonStatusEnum.
                    fromRecodeTypeAndRecodeCodeAndReviewTypeByConfigure(recordType, statusCode, recipe.getReviewType(), showUseDrugConfig, noHaveBuyDrugConfig, haveSendInfo);
            return buttonStatus.getPageButtonStatus();
        }else{
            LOGGER.error("当前按钮的显示信息不存在");
            return No_Show_Button;
        }

    }

    /**
     * 审核前置弹窗确认点击按钮是否审核通过的
     */
    @RpcService
    public Integer getCheckResult(Integer recipeId) {
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        Recipe recipe = recipeDAO.get(recipeId);
        if(null == recipe){
            LOGGER.error("该处方不存在！");
            return 0;
        }

        if(ReviewTypeConstant.Preposition_Check == recipe.getReviewType()){
            //date 2019/10/10
            //添加一次审核不通过标识位
            if(RecipeStatusConstant.READY_CHECK_YS == recipe.getStatus()){
                return 0;
            }else if (RecipeStatusConstant.CHECK_NOT_PASS_YS == recipe.getStatus()){
                if(RecipecCheckStatusConstant.First_Check_No_Pass == recipe.getCheckStatus()){
                    return 0;
                }else{
                    return 2;
                }
            }
        }
        return 1;
    }

    /**
     * 医生端处方列表根据tab展示
     * doctorId 医生ID
     * recipeId 上一页最后一条处方ID，首页传0
     * limit    每页限制数
     * tabStatus     电子处方列表tab状态
     *      0或不传-[ 全部 ]：包含所有状态的处方单
     *      1- [ 未签名 ]：暂存 [ 未签名 ] 的处方单
     *      2- [ 处理中 ]：其余所有状态的处方单
     *      3- [ 审核不通过 ]：[ 审核不通过 ] 的处方单
     *      4- [ 已结束 ]：包括 [ 已取消 ]、[ 已完成 ]、[ 已撤销 ] 的处方单
     * @return
     */
    @RpcService
    public List<Map<String, Object>> findRecipesForDoctorByTapstatus(Map<String,Integer> params) {
        if(params.get("doctorId")==null)   throw new DAOException("findRecipesForDoctor doctorId不允许为空");
        if(params.get("recipeId")==null)   throw new DAOException("findRecipesForDoctor recipeId不允许为空");
        if(params.get("limit")==null)   throw new DAOException("findRecipesForDoctor limit不允许为空");

        Integer doctorId=params.get("doctorId");
        Integer recipeId=params.get("recipeId");
        Integer limit=params.get("limit");
        Integer tapStatus=params.get("tapStatus");
        checkUserHasPermissionByDoctorId(doctorId);
        recipeId = (null == recipeId || Integer.valueOf(0).equals(recipeId)) ? Integer.valueOf(Integer.MAX_VALUE) : recipeId;

        List<Map<String, Object>> list = new ArrayList<>(0);
        PatientService patientService = ApplicationUtils.getBasicService(PatientService.class);
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        RecipeDetailDAO recipeDetailDAO = DAOFactory.getDAO(RecipeDetailDAO.class);
        RecipeOrderDAO orderDAO = DAOFactory.getDAO(RecipeOrderDAO.class);
        OrganDrugListDAO organDrugListDAO = DAOFactory.getDAO(OrganDrugListDAO.class);

        List<Recipe> recipeList = recipeDAO.findRecipesByTabstatusForDoctor(doctorId, recipeId, 0, limit,tapStatus);
        LOGGER.info("findRecipesForDoctor recipeList size={}", recipeList.size());
        if (CollectionUtils.isNotEmpty(recipeList)) {
            List<String> patientIds = new ArrayList<>(0);
            Map<Integer, RecipeBean> recipeMap = Maps.newHashMap();

            //date 20200506
            //获取处方对应的订单信息
            List<String> recipeCodes = recipeList.stream().map(recipe -> recipe.getOrderCode()).filter(code -> StringUtils.isNotEmpty(code)).collect(Collectors.toList());
            Map<String, Integer> orderStatus = new HashMap<>();
            if(CollectionUtils.isNotEmpty(recipeCodes)){

                List<RecipeOrder> recipeOrders = orderDAO.findValidListbyCodes(recipeCodes);
                orderStatus = recipeOrders.stream().collect(Collectors.toMap(RecipeOrder::getOrderCode, RecipeOrder::getStatus));
            }

            for (Recipe recipe : recipeList) {
                if (StringUtils.isNotEmpty(recipe.getMpiid())) {
                    patientIds.add(recipe.getMpiid());
                }
                //设置处方具体药品名称
                List<Recipedetail> recipedetails = recipeDetailDAO.findByRecipeId(recipe.getRecipeId());
                StringBuilder stringBuilder = new StringBuilder();
                if(null != recipedetails && recipedetails.size() > 0){
                    for (Recipedetail recipedetail : recipedetails) {
                        List<OrganDrugList> organDrugLists = organDrugListDAO.findByDrugIdAndOrganId(recipedetail.getDrugId(), recipe.getClinicOrgan());
                        if (organDrugLists != null && 0 < organDrugLists.size()) {
                            stringBuilder.append(organDrugLists.get(0).getSaleName());
                            if (StringUtils.isNotEmpty(organDrugLists.get(0).getDrugForm())) {
                                stringBuilder.append(organDrugLists.get(0).getDrugForm());
                            }
                        } else {
                            stringBuilder.append(recipedetail.getDrugName());
                        }
                        stringBuilder.append(" ").append(recipedetail.getDrugSpec()).append("/").append(recipedetail.getDrugUnit()).append("、");
                    }
                    stringBuilder.deleteCharAt(stringBuilder.lastIndexOf("、"));
                    recipe.setRecipeDrugName(stringBuilder.toString());
                }

                //前台页面展示的时间源不同
                recipe.setRecipeShowTime(recipe.getCreateDate());
                boolean effective = false;
                //只有审核未通过的情况需要看订单状态
                if (RecipeStatusConstant.CHECK_NOT_PASS_YS == recipe.getStatus()) {
                    effective = orderDAO.isEffectiveOrder(recipe.getOrderCode(), recipe.getPayMode());
                }
                //Map<String, String> tipMap = RecipeServiceSub.getTipsByStatus(recipe.getStatus(), recipe, effective);
                //date 20190929
                //修改医生端状态文案显示
                Map<String, String> tipMap = RecipeServiceSub.getTipsByStatusCopy(recipe.getStatus(), recipe, effective, (orderStatus == null || 0 >= orderStatus.size()) ? null : orderStatus.get(recipe.getOrderCode()));

                recipe.setShowTip(MapValueUtil.getString(tipMap, "listTips"));
                recipeMap.put(recipe.getRecipeId(), convertRecipeForRAP(recipe));
            }

            Map<String, PatientVO> patientMap = Maps.newHashMap();
            if (CollectionUtils.isNotEmpty(patientIds)) {
                List<PatientDTO> patientList = patientService.findByMpiIdIn(patientIds);
                if (CollectionUtils.isNotEmpty(patientList)) {
                    for (PatientDTO patient : patientList) {
                        //设置患者数据
                        RecipeServiceSub.setPatientMoreInfo(patient, doctorId);
                        patientMap.put(patient.getMpiId(), convertSensitivePatientForRAP(patient));
                    }
                }
            }

            for (Recipe recipe : recipeList) {
                String mpiId = recipe.getMpiid();
                HashMap<String, Object> map = Maps.newHashMap();
                map.put("recipe", recipeMap.get(recipe.getRecipeId()));
                map.put("patient", patientMap.get(mpiId));
                list.add(map);
            }
        }

        return list;
    }

}
