package recipe.service;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.ngari.base.doctor.service.IDoctorService;
import com.ngari.base.patient.model.PatientBean;
import com.ngari.base.patient.service.IPatientService;
import com.ngari.patient.dto.PatientDTO;
import com.ngari.patient.service.PatientService;
import com.ngari.patient.utils.ObjectCopyUtils;
import com.ngari.recipe.common.RecipeResultBean;
import com.ngari.recipe.entity.DrugList;
import com.ngari.recipe.entity.Recipe;
import com.ngari.recipe.entity.Recipedetail;
import com.ngari.recipe.recipe.model.PatientRecipeDTO;
import com.ngari.recipe.recipe.model.RecipeBean;
import com.ngari.recipe.recipe.model.RecipeDetailBean;
import com.ngari.recipe.recipe.model.RecipeRollingInfoBean;
import com.ngari.recipe.recipeorder.model.RecipeOrderBean;
import ctd.controller.exception.ControllerException;
import ctd.dictionary.Dictionary;
import ctd.dictionary.DictionaryController;
import ctd.persistence.DAOFactory;
import ctd.persistence.bean.QueryResult;
import ctd.util.annotation.RpcBean;
import ctd.util.annotation.RpcService;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.util.Assert;
import recipe.ApplicationUtils;
import recipe.constant.OrderStatusConstant;
import recipe.constant.ParameterConstant;
import recipe.constant.RecipeStatusConstant;
import recipe.dao.RecipeDAO;
import recipe.dao.RecipeDetailDAO;
import recipe.dao.RecipeOrderDAO;
import recipe.dao.bean.PatientRecipeBean;
import recipe.dao.bean.RecipeRollingInfo;
import recipe.service.common.RecipeCacheService;
import recipe.util.DateConversion;
import recipe.util.MapValueUtil;

import java.util.*;

import static recipe.service.RecipeServiceSub.convertPatientForRAP;
import static recipe.service.RecipeServiceSub.convertRecipeForRAP;

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
        recipeId = (null == recipeId || Integer.valueOf(0).equals(recipeId)) ? Integer.valueOf(Integer.MAX_VALUE) : recipeId;

        List<Map<String, Object>> list = new ArrayList<>(0);
        PatientService patientService = ApplicationUtils.getBasicService(PatientService.class);
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        RecipeDetailDAO recipeDetailDAO = DAOFactory.getDAO(RecipeDetailDAO.class);
        RecipeOrderDAO orderDAO = DAOFactory.getDAO(RecipeOrderDAO.class);

        List<Recipe> recipeList = recipeDAO.findRecipesForDoctor(doctorId, recipeId, 0, limit);
        LOGGER.info("findRecipesForDoctor recipeList size={}", recipeList.size());
        if (CollectionUtils.isNotEmpty(recipeList)) {
            List<String> patientIds = new ArrayList<>(0);
            Map<Integer, RecipeBean> recipeMap = Maps.newHashMap();
            for (Recipe recipe : recipeList) {
                if (StringUtils.isNotEmpty(recipe.getMpiid())) {
                    patientIds.add(recipe.getMpiid());
                }
                //设置处方具体药品名称
                recipe.setRecipeDrugName(recipeDetailDAO.getDrugNamesByRecipeId(recipe.getRecipeId()));
                //前台页面展示的时间源不同
                recipe.setRecipeShowTime(recipe.getCreateDate());
                boolean effective = false;
                //只有审核未通过的情况需要看订单状态
                if (RecipeStatusConstant.CHECK_NOT_PASS_YS == recipe.getStatus()) {
                    effective = orderDAO.isEffectiveOrder(recipe.getOrderCode(), recipe.getPayMode());
                }
                Map<String, String> tipMap = RecipeServiceSub.getTipsByStatus(recipe.getStatus(), recipe, effective);
                recipe.setShowTip(MapValueUtil.getString(tipMap, "listTips"));
                recipeMap.put(recipe.getRecipeId(), convertRecipeForRAP(recipe));
            }

            Map<String, PatientDTO> patientMap = Maps.newHashMap();
            if (CollectionUtils.isNotEmpty(patientIds)) {
                List<PatientDTO> patientList = patientService.findByMpiIdIn(patientIds);
                if (CollectionUtils.isNotEmpty(patientList)) {
                    for (PatientDTO patient : patientList) {
                        //设置患者数据
                        RecipeServiceSub.setPatientMoreInfo(patient, doctorId);
                        patientMap.put(patient.getMpiId(), convertPatientForRAP(patient));
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

        List<PatientRecipeDTO> otherRecipes = this.findOtherRecipesForPatient(mpiId, 0, 1);
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
    public List<PatientRecipeDTO> findOtherRecipesForPatient(String mpiId, Integer index, Integer limit) {
        Assert.hasLength(mpiId, "findOtherRecipesForPatient mpiId is null.");
        RecipeService recipeService = ApplicationUtils.getRecipeService(RecipeService.class);
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);

        List<String> allMpiIds = recipeService.getAllMemberPatientsByCurrentPatient(mpiId);
        //获取待处理那边最新的一单
        List<Integer> recipeIds = recipeDAO.findPendingRecipes(allMpiIds, RecipeStatusConstant.CHECK_PASS, 0, 1);
        List<PatientRecipeBean> backList = recipeDAO.findOtherRecipesForPatient(allMpiIds, recipeIds, index, limit);
        return processListDate(backList, allMpiIds);
    }

    /**
     * 获取所有处方单信息
     *
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
     * @param backList
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
                                    LOGGER.warn("processListDate KuaiDiNiaoCode get error. code={}", order.getLogisticsCompany());
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
        IDoctorService iDoctorService = ApplicationUtils.getBaseService(IDoctorService.class);
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
        IDoctorService iDoctorService = ApplicationUtils.getBaseService(IDoctorService.class);

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
            default:
                msg = "未知状态";
        }

        return msg;
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
        RecipeDetailDAO recipeDetailDAO = DAOFactory.getDAO(RecipeDetailDAO.class);
        RecipeOrderDAO orderDAO = DAOFactory.getDAO(RecipeOrderDAO.class);
        PatientService patientService = ApplicationUtils.getBasicService(PatientService.class);

        List<Map<String, Object>> list = new ArrayList<>();
        List<Recipe> recipes = recipeDAO.findRecipeListByDoctorAndPatient(doctorId, mpiId, start, limit);
        PatientDTO patient = RecipeServiceSub.convertPatientForRAP(patientService.get(mpiId));
        if (CollectionUtils.isNotEmpty(recipes)) {
            for (Recipe recipe : recipes) {
                Map<String, Object> map = Maps.newHashMap();
                recipe.setRecipeDrugName(recipeDetailDAO.getDrugNamesByRecipeId(recipe.getRecipeId()));
                recipe.setRecipeShowTime(recipe.getCreateDate());
                boolean effective = false;
                //只有审核未通过的情况需要看订单状态
                if (RecipeStatusConstant.CHECK_NOT_PASS_YS == recipe.getStatus()) {
                    effective = orderDAO.isEffectiveOrder(recipe.getOrderCode(), recipe.getPayMode());
                }
                Map<String, String> tipMap = RecipeServiceSub.getTipsByStatus(recipe.getStatus(), recipe, effective);
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
    @RpcService
    public List<PatientDTO> findHistoryPatientsFromRecipeByDoctor(Integer doctorId, int start, int limit) {
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        final List<String> mpiList = recipeDAO.findHistoryMpiIdsByDoctorId(doctorId, start, limit);
        if (mpiList.size() == 0) {
            return new ArrayList<>();
        }
        PatientService patientService = ApplicationUtils.getBasicService(PatientService.class);
        return patientService.getPatients(mpiList, doctorId);
    }

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
                for (Recipedetail detail : recipedetails){
                    drugInfo = Maps.newHashMap();
                    drugInfo.put("drugName",detail.getDrugName());
                    //开药总量+药品单位
                    String dSpec = "*"+detail.getUseTotalDose().intValue() + detail.getDrugUnit();
                    drugInfo.put("drugTotal",dSpec);
                    String useWay = "用法：每次" + detail.getUseDose() + detail.getUseDoseUnit()
                   +"/"+usingRateDic.getText(detail.getUsingRate())
                    +"/"+usePathwaysDic.getText(detail.getUsePathways())
                    +detail.getUseDays() + "天";
                    drugInfo.put("useWay",useWay);
                    drugInfoList.add(drugInfo);
                }
                map.put("rp", drugInfoList);
                map.put("memo",recipe.getMemo());
                switch (recipe.getStatus()){
                    case RecipeStatusConstant.CHECK_PASS:
                        map.put("statusText","请尽快去医院药房窗口取药");
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
                    default:
                        map.put("statusText",DictionaryController.instance().get("eh.cdr.dictionary.RecipeStatus").getText(recipe.getStatus()));
                        break;
                }
                mapList.add(map);
            }
            result.put("list",mapList);
        }catch (Exception e){
            LOGGER.error("findAllRecipesForPatient error"+e.getMessage());
        }

        return result;
    }

}
