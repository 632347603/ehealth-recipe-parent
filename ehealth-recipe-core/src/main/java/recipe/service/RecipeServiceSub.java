package recipe.service;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.ngari.base.BaseAPI;
import com.ngari.base.doctor.service.IDoctorService;
import com.ngari.base.operationrecords.model.OperationRecordsBean;
import com.ngari.base.operationrecords.service.IOperationRecordsService;
import com.ngari.base.organconfig.service.IOrganConfigService;
import com.ngari.base.patient.service.IPatientService;
import com.ngari.base.property.service.IConfigurationCenterUtilsService;
import com.ngari.base.serviceconfig.mode.ServiceConfigResponseTO;
import com.ngari.base.serviceconfig.service.IHisServiceConfigService;
import com.ngari.common.dto.RecipeTagMsgBean;
import com.ngari.consult.ConsultAPI;
import com.ngari.consult.ConsultBean;
import com.ngari.consult.common.service.IConsultService;
import com.ngari.consult.message.service.IConsultMessageService;
import com.ngari.follow.service.IRelationPatientService;
import com.ngari.follow.vo.RelationDoctorVO;
import com.ngari.home.asyn.model.BussCancelEvent;
import com.ngari.home.asyn.service.IAsynDoBussService;
import com.ngari.patient.dto.*;
import com.ngari.patient.service.*;
import com.ngari.patient.utils.ObjectCopyUtils;
import com.ngari.recipe.audit.model.AuditMedicineIssueDTO;
import com.ngari.recipe.audit.model.AuditMedicinesDTO;
import com.ngari.recipe.common.RecipeResultBean;
import com.ngari.recipe.entity.*;
import com.ngari.recipe.recipe.model.*;
import ctd.dictionary.Dictionary;
import ctd.dictionary.DictionaryController;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.schema.exception.ValidateException;
import ctd.util.AppContextHolder;
import ctd.util.FileAuth;
import ctd.util.JSONUtils;
import networkclinic.api.service.INetworkclinicMsgService;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.ObjectUtils;
import recipe.ApplicationUtils;
import recipe.audit.bean.PAWebRecipeDanger;
import recipe.audit.service.PrescriptionService;
import recipe.bean.DrugEnterpriseResult;
import recipe.bussutil.RecipeUtil;
import recipe.bussutil.RecipeValidateUtil;
import recipe.constant.*;
import recipe.dao.*;
import recipe.drugsenterprise.AldyfRemoteService;
import recipe.hisservice.HisMqRequestInit;
import recipe.hisservice.RecipeToHisMqService;
import recipe.purchase.PurchaseService;
import recipe.recipecheck.RecipeCheckService;
import recipe.service.common.RecipeCacheService;
import recipe.thread.PushRecipeToRegulationCallable;
import recipe.thread.RecipeBusiThreadPool;
import recipe.util.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 供recipeService调用
 *
 * @author liuya
 */
public class RecipeServiceSub {

    private static final Logger LOGGER = LoggerFactory.getLogger(RecipeServiceSub.class);

    private static final String UNSIGN = "unsign";

    private static final String UNCHECK = "uncheck";

    private static PatientService patientService = ApplicationUtils.getBasicService(PatientService.class);

    private static DoctorService doctorService = ApplicationUtils.getBasicService(DoctorService.class);

    private static OrganService organService = ApplicationUtils.getBasicService(OrganService.class);

    private static IPatientService iPatientService = ApplicationUtils.getBaseService(IPatientService.class);

    private static IDoctorService iDoctorService = ApplicationUtils.getBaseService(IDoctorService.class);

    private static RecipeCacheService cacheService = ApplicationUtils.getRecipeService(RecipeCacheService.class);

    private static DepartmentService departmentService = ApplicationUtils.getBasicService(DepartmentService.class);

    private static IConfigurationCenterUtilsService configService = ApplicationUtils.getBaseService(IConfigurationCenterUtilsService.class);

    private static Integer[] showRecipeStatus = new Integer[]{RecipeStatusConstant.CHECK_PASS_YS, RecipeStatusConstant.IN_SEND, RecipeStatusConstant.WAIT_SEND, RecipeStatusConstant.FINISH};

    private static Integer[] showDownloadRecipeStatus = new Integer[]{RecipeStatusConstant.CHECK_PASS_YS, RecipeStatusConstant.RECIPE_DOWNLOADED};

    /**
     * @param recipeBean
     * @param detailBeanList
     * @param flag(recipe的fromflag) 0：HIS处方  1：平台处方
     * @return
     */
    public static Integer saveRecipeDataImpl(RecipeBean recipeBean, List<RecipeDetailBean> detailBeanList, Integer flag) {
        if (null != recipeBean && recipeBean.getRecipeId() != null && recipeBean.getRecipeId() > 0) {
            RecipeService recipeService = ApplicationUtils.getRecipeService(RecipeService.class);
            return recipeService.updateRecipeAndDetail(recipeBean, detailBeanList);
        }
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        if (null == recipeBean) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "recipe is required!");
        }
        Recipe recipe = ObjectCopyUtils.convert(recipeBean, Recipe.class);
        List<Recipedetail> details = ObjectCopyUtils.convert(detailBeanList, Recipedetail.class);

        setRecipeMoreInfo(recipe,details,recipeBean,flag);

        Integer recipeId = recipeDAO.updateOrSaveRecipeAndDetail(recipe, details, false);
        recipe.setRecipeId(recipeId);

        //武昌需求，加入处方扩展信息
        RecipeExtendBean recipeExt = recipeBean.getRecipeExtend();
        if(null != recipeExt && null != recipeId) {
            RecipeExtend recipeExtend = ObjectCopyUtils.convert(recipeExt, RecipeExtend.class);
            recipeExtend.setRecipeId(recipeId);
            RecipeExtendDAO recipeExtendDAO = DAOFactory.getDAO(RecipeExtendDAO.class);
            recipeExtendDAO.saveOrUpdateRecipeExtend(recipeExtend);
        }

        //加入历史患者
        String mpiId = recipe.getMpiid();
        PatientDTO patient = patientService.get(mpiId);
        saveOperationRecordsForRecipe(mpiId,patient,recipe);
        RecipeLogService.saveRecipeLog(recipeId, recipe.getStatus(), recipe.getStatus(), "暂存处方单");
        return recipeId;
    }

    public static void setRecipeMoreInfo(Recipe recipe, List<Recipedetail> details, RecipeBean recipeBean, Integer flag) {
        //校验处方和明细保存数据
        validateRecipeAndDetailData(recipe,details);

        //设置处方默认数据
        RecipeUtil.setDefaultData(recipe);
        //设置处方明细数据
        if(details != null && details.size() > 0){
            setReciepeDetailsInfo(flag,recipeBean,recipe,details);
        }

        //患者数据前面已校验--设置患者姓名医生姓名机构名
        PatientDTO patient = patientService.get(recipe.getMpiid());
        recipe.setPatientName(patient.getPatientName());
        recipe.setDoctorName(doctorService.getNameById(recipe.getDoctor()));
        OrganDTO organBean = organService.get(recipe.getClinicOrgan());
        recipe.setOrganName(organBean.getShortName());

        //武昌机构recipeCode平台生成
        getRecipeCodeForWuChang(recipeBean,patient,recipe);

        // 根据咨询单特殊来源标识设置处方单特殊来源标识
        if (null != recipe.getClinicId()) {
            IConsultService consultService = ConsultAPI.getService(IConsultService.class);
            ConsultBean consultBean = consultService.getById(recipe.getClinicId());
            if ((null != consultBean) && (Integer.valueOf(1).equals(consultBean.getConsultSource()))) {
                recipe.setRecipeSource(consultBean.getConsultSource());
            }
        }
        RecipeService recipeService = ApplicationUtils.getRecipeService(RecipeService.class);
        recipeService.setMergeDrugType(details, recipe);
    }

    private static void saveOperationRecordsForRecipe(String mpiId, PatientDTO patient, Recipe recipe) {
        IOperationRecordsService iOperationRecordsService = ApplicationUtils.getBaseService(IOperationRecordsService.class);
        OperationRecordsBean record = new OperationRecordsBean();
        record.setMpiId(mpiId);
        record.setRequestMpiId(mpiId);
        record.setPatientName(patient.getPatientName());
        record.setBussType(BussTypeConstant.RECIPE);
        record.setBussId(recipe.getRecipeId());
        record.setRequestDoctor(recipe.getDoctor());
        record.setExeDoctor(recipe.getDoctor());
        record.setRequestTime(recipe.getCreateDate());
        iOperationRecordsService.saveOperationRecordsForRecipe(record);
    }

    private static void getRecipeCodeForWuChang(RecipeBean recipeBean, PatientDTO patient, Recipe recipe) {
        RedisClient redisClient = RedisClient.instance();
        Set<String> organIdList = redisClient.sMembers(CacheConstant.KEY_WUCHANG_ORGAN_LIST);
        //武昌机构recipeCode平台生成
        if (RecipeBussConstant.FROMFLAG_HIS_USE.equals(recipeBean.getFromflag())
                || (CollectionUtils.isNotEmpty(organIdList) && organIdList.contains(recipe.getClinicOrgan().toString()))) {
            //在 doSignRecipe 生成的一些数据在此生成
            PatientDTO requestPatient = patientService.getOwnPatientForOtherProject(patient.getLoginId());
            if (null != requestPatient && null != requestPatient.getMpiId()) {
                recipe.setRequestMpiId(requestPatient.getMpiId());
                // urt用于系统消息推送
                recipe.setRequestUrt(requestPatient.getUrt());
            }
            //生成处方编号，不需要通过HIS去产生
            String recipeCodeStr = "ngari" + DigestUtil.md5For16(recipeBean.getClinicOrgan() +
                    recipeBean.getMpiid() + Calendar.getInstance().getTimeInMillis());
            recipe.setRecipeCode(recipeCodeStr);
            recipeBean.setRecipeCode(recipeCodeStr);
        }
    }

    private static void setReciepeDetailsInfo(Integer flag, RecipeBean recipeBean, Recipe recipe, List<Recipedetail> details) {
        if (RecipeBussConstant.FROMFLAG_PLATFORM.equals(flag)
                || RecipeBussConstant.FROMFLAG_HIS_USE.equals(flag)) {
            //设置药品详情数据
            boolean isSucc = setDetailsInfo(recipe, details);
            if (!isSucc) {
                throw new DAOException(ErrorCode.SERVICE_ERROR, "药品详情数据有误");
            }
            recipeBean.setTotalMoney(recipe.getTotalMoney());
            recipeBean.setActualPrice(recipe.getActualPrice());
        } else if (RecipeBussConstant.FROMFLAG_HIS.equals(flag)) {
            //处方总价未计算
            BigDecimal totalMoney = new BigDecimal(0d);
            for (Recipedetail detail : details) {
                if (null != detail.getDrugCost()) {
                    totalMoney = totalMoney.add(detail.getDrugCost());
                }
            }
            recipe.setTotalMoney(totalMoney);
            recipe.setActualPrice(totalMoney);
        }
    }

    private static void validateRecipeAndDetailData(Recipe recipe, List<Recipedetail> details) {
        RecipeValidateUtil.validateSaveRecipeData(recipe);
        if (null != details) {
            for (Recipedetail recipeDetail : details) {
                RecipeValidateUtil.validateRecipeDetailData(recipeDetail, recipe);
            }
        }
    }


    /**
     * 设置药品详情数据
     *
     * @param recipe        处方
     * @param recipedetails 处方ID
     */
    public static boolean setDetailsInfo(Recipe recipe, List<Recipedetail> recipedetails) {
        boolean success = false;
        int organId = recipe.getClinicOrgan();
        //药品总金额
        BigDecimal totalMoney = new BigDecimal(0d);
        List<Integer> drugIds = new ArrayList<>(recipedetails.size());
        List<String> organDrugCodes = new ArrayList<>(recipedetails.size());
        Date nowDate = DateTime.now().toDate();
        String recipeMode = recipe.getRecipeMode();
        
        for (Recipedetail detail : recipedetails) {
            //设置药品详情基础数据
            detail.setStatus(1);
            detail.setRecipeId(recipe.getRecipeId());
            detail.setCreateDt(nowDate);
            detail.setLastModify(nowDate);
            if (null != detail.getDrugId()) {
                drugIds.add(detail.getDrugId());
            }
            if (StringUtils.isNotEmpty(detail.getOrganDrugCode())) {
                organDrugCodes.add(detail.getOrganDrugCode());
            }
        }

        if (CollectionUtils.isNotEmpty(drugIds)) {
            OrganDrugListDAO organDrugListDAO = DAOFactory.getDAO(OrganDrugListDAO.class);
            DrugListDAO drugListDAO = DAOFactory.getDAO(DrugListDAO.class);
            //判断平台基础库药品是否删除
            List<DrugList> drugLists = drugListDAO.findByDrugIdsforDel(drugIds);
            if (CollectionUtils.isNotEmpty(drugLists)){
                List<String> delDrugName = Lists.newArrayList();
                for (DrugList drugList :drugLists){
                    delDrugName.add(drugList.getDrugName());
                }
                if (CollectionUtils.isNotEmpty(delDrugName)){
                    String errorDrugName = Joiner.on(",").join(delDrugName);
                    throw new DAOException(ErrorCode.SERVICE_ERROR, errorDrugName + "药品已失效，请重新选择药品");
                }
            }
            //是否为老的药品兼容方式，老的药品传入方式没有organDrugCode
            boolean oldFlag = organDrugCodes.isEmpty() ? true : false;
            Map<String, OrganDrugList> organDrugListMap = Maps.newHashMap();
            Map<Integer, OrganDrugList> organDrugListIdMap = Maps.newHashMap();
            List<OrganDrugList> organDrugList = Lists.newArrayList();
            if(oldFlag){
                organDrugList = organDrugListDAO.findByOrganIdAndDrugIds(organId, drugIds);
            } else{
                organDrugList = organDrugListDAO.findByOrganIdAndDrugCodes(organId, organDrugCodes);
            }

            if (CollectionUtils.isNotEmpty(organDrugList)) {
                if(RecipeBussConstant.RECIPEMODE_NGARIHEALTH.equals(recipeMode)) {
                    //药品有混非外带药和外带药的不能一起开
                    int takeMedicineSize = 0;
                    List<String> takeOutDrugName = Lists.newArrayList();
                    for (OrganDrugList obj : organDrugList) {
                        //检验是否都为外带药
                        if (Integer.valueOf(1).equals(obj.getTakeMedicine())) {
                            takeMedicineSize++;
                            takeOutDrugName.add(obj.getSaleName());
                        }
                        organDrugListMap.put(obj.getOrganDrugCode(), obj);
                        organDrugListIdMap.put(obj.getDrugId(), obj);
                    }

                    if (takeMedicineSize > 0) {
                        if (takeMedicineSize != organDrugList.size()) {
                            String errorDrugName = Joiner.on(",").join(takeOutDrugName);
                            //外带药和处方药混合开具是不允许的
                            LOGGER.warn("setDetailsInfo 存在外带药且混合开具. recipeId=[{}], drugIds={}, 外带药={}", recipe.getRecipeId(),
                                    JSONUtils.toString(drugIds), errorDrugName);
                            throw new DAOException(ErrorCode.SERVICE_ERROR, errorDrugName + "为外带药,不能与其他药品开在同一张处方单上");
                        } else {
                            //外带处方， 同时也设置成只能配送处方
                            recipe.setTakeMedicine(1);
                            recipe.setDistributionFlag(1);
                        }
                    }
                    //判断某诊断下某药品能否开具
                    if(recipe != null && recipe.getOrganDiseaseName() != null){
                        canOpenRecipeDrugsAndDisease(recipe,drugIds);
                    }
                }else{
                    for (OrganDrugList obj : organDrugList) {
                        organDrugListMap.put(obj.getOrganDrugCode(), obj);
                        organDrugListIdMap.put(obj.getDrugId(), obj);
                    }
                }


                OrganDrugList organDrug;
                List<String> delOrganDrugName = Lists.newArrayList();
                for (Recipedetail detail : recipedetails) {
                    //设置药品基础数据
                    if(oldFlag){
                        organDrug = organDrugListIdMap.get(detail.getDrugId());
                    } else{
                        organDrug = organDrugListMap.get(detail.getOrganDrugCode());
                    }
                    if (null != organDrug) {
                        detail.setOrganDrugCode(organDrug.getOrganDrugCode());
                        detail.setDrugName(organDrug.getDrugName());
                        detail.setDrugSpec(organDrug.getDrugSpec());
                        detail.setDrugUnit(organDrug.getUnit());
                        detail.setDefaultUseDose(organDrug.getUseDose());
                        detail.setUseDoseUnit(organDrug.getUseDoseUnit());
                        detail.setDosageUnit(organDrug.getUseDoseUnit());
                        //设置药品包装数量
                        detail.setPack(organDrug.getPack());
                        //中药基础数据处理
                        if (RecipeBussConstant.RECIPETYPE_TCM.equals(recipe.getRecipeType())) {
                            detail.setUsePathways(recipe.getTcmUsePathways());
                            detail.setUsingRate(recipe.getTcmUsingRate());

                            //date 20200526
                            //构建处方初始化处方药品详情的时候用recipe的剂数
//                            if(null != recipe.getCopyNum()){
//                                detail.setUseDays(new BigDecimal(recipe.getCopyNum()));
//                            }
                            detail.setUseDays(recipe.getCopyNum());
                            if (detail.getUseDose() !=null){
                                detail.setUseTotalDose(BigDecimal.valueOf(recipe.getCopyNum()).multiply(BigDecimal.valueOf(detail.getUseDose())).doubleValue());
                            }
                        } else if (RecipeBussConstant.RECIPETYPE_HP.equals(recipe.getRecipeType())) {
                            //date 20200526
                            //构建处方初始化处方药品详情的时候用recipe的剂数
//                            if(null != recipe.getCopyNum()){
//                                detail.setUseDays(new BigDecimal(recipe.getCopyNum()));
//                            }
                            detail.setUseDays(recipe.getCopyNum());
                            if (detail.getUseDose() !=null){
                                detail.setUseTotalDose(BigDecimal.valueOf(recipe.getCopyNum()).multiply(BigDecimal.valueOf(detail.getUseDose())).doubleValue());
                            }
                        }

                        //添加机构药品信息
                        //date 20200225
                        detail.setProducer(organDrug.getProducer());
                        detail.setProducerCode(organDrug.getProducerCode());
                        detail.setLicenseNumber(organDrug.getLicenseNumber());

                        //设置药品价格
                        BigDecimal price = organDrug.getSalePrice();
                        if (null == price) {
                            LOGGER.warn("setDetailsInfo 药品ID：" + organDrug.getDrugId() + " 在医院(ID为" + organId + ")的价格为NULL！");
                            throw new DAOException(ErrorCode.SERVICE_ERROR, "药品数据异常！");
                        }
                        detail.setSalePrice(price);
                        //保留3位小数
                        BigDecimal drugCost = price.multiply(new BigDecimal(detail.getUseTotalDose()))
                                .divide(BigDecimal.ONE, 3, RoundingMode.UP);
                        detail.setDrugCost(drugCost);
                        totalMoney = totalMoney.add(drugCost);
                    }else {
                        if (StringUtils.isNotEmpty(organDrug.getDrugName())){
                            delOrganDrugName.add(organDrug.getDrugName());
                        }
                    }

                    //date 202000601
                    //设置处方用药天数字符类型
                    if(StringUtils.isEmpty(detail.getUseDaysB())){

                        detail.setUseDaysB(null != detail.getUseDays() ? detail.getUseDays().toString() : "0");

                    }
                }
                if (CollectionUtils.isNotEmpty(delOrganDrugName)){
                    String errorDrugName = Joiner.on(",").join(delOrganDrugName);
                    throw new DAOException(ErrorCode.SERVICE_ERROR, errorDrugName + "药品已失效，请重新选择药品");
                }
                success = true;
            } else {
                LOGGER.warn("setDetailsInfo organDrugList. recipeId=[{}], drugIds={}", recipe.getRecipeId(), JSONUtils.toString(drugIds));
                throw new DAOException("药品已失效，请重新选择药品");
            }
        } else {
            LOGGER.warn("setDetailsInfo 详情里没有药品ID. recipeId=[{}]", recipe.getRecipeId());
        }

        recipe.setTotalMoney(totalMoney);
        recipe.setActualPrice(totalMoney);
        return success;
    }

    public static RecipeResultBean validateRecipeSendDrugMsg(RecipeBean recipe){
        RecipeResultBean resultBean = RecipeResultBean.getSuccess();
        RecipeDetailDAO detailDAO = DAOFactory.getDAO(RecipeDetailDAO.class);
        List<Integer> drugIds = detailDAO.findDrugIdByRecipeId(recipe.getRecipeId());
        try {
            //处方药品能否配送以及能否开具同一张处方上
            canOpenRecipeDrugs(recipe.getClinicOrgan(),recipe.getRecipeId(),drugIds);
        }catch (Exception e){
            LOGGER.error("canOpenRecipeDrugs error",e);
            resultBean.setCode(RecipeResultBean.FAIL);
            resultBean.setMsg(e.getMessage());
            return resultBean;
        }
        /*//平台增加药品相关校验
        if(RecipeBussConstant.RECIPEMODE_NGARIHEALTH.equals(recipeMode)) {

            DrugsEnterpriseService drugsEnterpriseService = ApplicationUtils.getRecipeService(DrugsEnterpriseService.class);
            boolean checkEnterprise = drugsEnterpriseService.checkEnterprise(recipe.getClinicOrgan());
            if (checkEnterprise) {
                //判断药品能否开在一张处方单上
                result.putAll(canOpenRecipeDrugsCopy(recipe.getClinicOrgan(),recipe.getRecipeId(),drugIds));
            }
        } else if(RecipeBussConstant.RECIPEMODE_ZJJGPT.equals(recipeMode)) {
            //浙江省互联网医院模式不需要这么多校验
//            for (OrganDrugList obj : organDrugList) {
//                organDrugListMap.put(obj.getOrganDrugCode(), obj);
//                organDrugListIdMap.put(obj.getDrugId(), obj);
//            }
            //无需校验
            result.put("code", "200");
        }*/
        return resultBean;
    }

    private static void canOpenRecipeDrugsAndDisease(Recipe recipe, List<Integer> drugIds) {
        List<String> nameLists = Splitter.on("；").splitToList(recipe.getOrganDiseaseName());
        DrugListDAO drugListDAO = DAOFactory.getDAO(DrugListDAO.class);
        for (String organDiseaseName : nameLists){
            Set<String> drugIdSet = cacheService.findDrugByDiseaseName(recipe.getClinicOrgan()+"_"+organDiseaseName);
            if (CollectionUtils.isEmpty(drugIdSet)){continue;}
            for (String drugId:drugIdSet){
                if (drugIds.contains(Integer.valueOf(drugId))){
                    DrugList drugList = drugListDAO.getById(Integer.valueOf(drugId));
                    throw new DAOException(ErrorCode.SERVICE_ERROR,"本处方中:"+drugList.getDrugName()+"对诊断为["+organDiseaseName+"]的患者禁用,请修改处方,如确认无误请联系管理员");
                }
            }
        }
    }

    public static void canOpenRecipeDrugs(Integer clinicOrgan, Integer recipeId, List<Integer> drugIds) {
        DrugListDAO drugListDAO = DAOFactory.getDAO(DrugListDAO.class);
        SaleDrugListDAO saleDrugListDAO = DAOFactory.getDAO(SaleDrugListDAO.class);
        List<DrugList> drugList = drugListDAO.findByDrugIds(drugIds);
       /* Map<Integer, DrugList> drugListMap = Maps.newHashMap();
        for (DrugList obj : drugList) {
            drugListMap.put(obj.getDrugId(), obj);
        }*/
        //list转map
        Map<Integer, DrugList> drugListMap = drugList.stream().collect(Collectors.toMap(DrugList::getDrugId, a -> a));


        //供应商一致性校验，取第一个药品能配送的药企作为标准
        //应该按照机构配置了的药企作为条件来查找是否能配送
        //获取该机构下配置的药企
        OrganAndDrugsepRelationDAO relationDAO = DAOFactory.getDAO(OrganAndDrugsepRelationDAO.class);
        List<DrugsEnterprise> enterprises = relationDAO.findDrugsEnterpriseByOrganIdAndStatus(clinicOrgan, 1);
        List<Integer> deps = enterprises.stream().map(e->e.getId()).collect(Collectors.toList());
        //找到每一个药能支持的药企关系
        Map<Integer, List<String>> drugDepRel = saleDrugListDAO.findDrugDepRelation(drugIds,deps);

        //无法配送药品校验------有一个药企能支持就不会提示
        List<String> noFilterDrugName = new ArrayList<>();
        for (Integer drugId : drugIds) {
            if (CollectionUtils.isEmpty(drugDepRel.get(drugId))) {
                noFilterDrugName.add(drugListMap.get(drugId).getDrugName());
            }
        }
        if (CollectionUtils.isNotEmpty(noFilterDrugName)) {
            LOGGER.warn("setDetailsInfo 存在无法配送的药品. recipeId=[{}], drugIds={}, noFilterDrugName={}",
                    recipeId, JSONUtils.toString(drugIds), JSONUtils.toString(noFilterDrugName));
            throw new DAOException(ErrorCode.SERVICE_ERROR, Joiner.on(",").join(noFilterDrugName) + "不在该机构可配送药企的药品目录里面，无法进行配送");
        }

        noFilterDrugName.clear();
        //取第一个药能支持的药企做标准来判断
        List<String> firstDrugDepIds = drugDepRel.get(drugIds.get(0));
        for (Integer drugId : drugDepRel.keySet()) {
            List<String> depIds = drugDepRel.get(drugId);
            boolean filterFlag = false;
            for (String depId : depIds) {
                //匹配到一个药企相同则可跳过
                if (firstDrugDepIds.contains(depId)) {
                    filterFlag = true;
                    break;
                }
            }
            if (!filterFlag) {
                noFilterDrugName.add(drugListMap.get(drugId).getDrugName());
            } else {
                //取交集
                firstDrugDepIds.retainAll(depIds);
            }
        }

        if (CollectionUtils.isNotEmpty(noFilterDrugName)) {
            List<DrugList> drugLists = new ArrayList<DrugList>(drugListMap.values());
            List<String> drugNames = drugLists.stream().map(e->e.getDrugName()).collect(Collectors.toList());
            LOGGER.error("setDetailsInfo 存在无法一起配送的药品. recipeId=[{}], drugIds={}, noFilterDrugName={}",
                    recipeId, JSONUtils.toString(drugIds), JSONUtils.toString(noFilterDrugName));
            //一张处方单上的药品不能同时支持同一家药企配送
            //throw new DAOException(ErrorCode.SERVICE_ERROR, Joiner.on(",").join(noFilterDrugName) + "不能开具在一张处方上！");
            throw new DAOException(ErrorCode.SERVICE_ERROR, Joiner.on(",").join(drugNames) + "不支持同一家药企配送");
        }
    }

    /*public static Map<String, String> canOpenRecipeDrugsCopy(Integer clinicOrgan, Integer recipeId, List<Integer> drugIds) {
        Map<String, String> result = new HashMap<>();
        DrugListDAO drugListDAO = DAOFactory.getDAO(DrugListDAO.class);
        SaleDrugListDAO saleDrugListDAO = DAOFactory.getDAO(SaleDrugListDAO.class);
        List<DrugList> drugList = drugListDAO.findByDrugIds(drugIds);
       *//* Map<Integer, DrugList> drugListMap = Maps.newHashMap();
        for (DrugList obj : drugList) {
            drugListMap.put(obj.getDrugId(), obj);
        }*//*
        //list转map
        Map<Integer, DrugList> drugListMap = drugList.stream().collect(Collectors.toMap(DrugList::getDrugId, a -> a));


        //供应商一致性校验，取第一个药品能配送的药企作为标准
        //应该按照机构配置了的药企作为条件来查找是否能配送
        //获取该机构下配置的药企
        OrganAndDrugsepRelationDAO relationDAO = DAOFactory.getDAO(OrganAndDrugsepRelationDAO.class);
        List<DrugsEnterprise> enterprises = relationDAO.findDrugsEnterpriseByOrganIdAndStatus(clinicOrgan, 1);
        List<Integer> deps = enterprises.stream().map(e->e.getId()).collect(Collectors.toList());
        //找到每一个药能支持的药企关系
        Map<Integer, List<String>> drugDepRel = saleDrugListDAO.findDrugDepRelation(drugIds,deps);

        //无法配送药品校验
        List<String> noFilterDrugName = new ArrayList<>();
        for (Integer drugId : drugIds) {
            if (CollectionUtils.isEmpty(drugDepRel.get(drugId))) {
                noFilterDrugName.add(drugListMap.get(drugId).getDrugName());
            }
        }
        if (CollectionUtils.isNotEmpty(noFilterDrugName)) {
            LOGGER.warn("setDetailsInfo 存在无法配送的药品. recipeId=[{}], drugIds={}, noFilterDrugName={}",
                    recipeId, JSONUtils.toString(drugIds), JSONUtils.toString(noFilterDrugName));
            result.put("code", "609");
            result.put("msg", Joiner.on(",").join(noFilterDrugName) + "药品不在该机构可配送药企的药品目录里面，无法进行配送");
        }

        noFilterDrugName.clear();
        //取第一个药能支持的药企做标准来判断
        List<String> firstDrugDepIds = drugDepRel.get(drugIds.get(0));
        for (Integer drugId : drugDepRel.keySet()) {
            List<String> depIds = drugDepRel.get(drugId);
            boolean filterFlag = false;
            for (String depId : depIds) {
                //匹配到一个药企相同则可跳过
                if (firstDrugDepIds.contains(depId)) {
                    filterFlag = true;
                    break;
                }
            }
            if (!filterFlag) {
                noFilterDrugName.add(drugListMap.get(drugId).getDrugName());
            } else {
                //取交集
                firstDrugDepIds.retainAll(depIds);
            }
        }

        if (CollectionUtils.isNotEmpty(noFilterDrugName)) {
            List<DrugList> drugLists = new ArrayList<DrugList>(drugListMap.values());
            List<String> drugNames = drugLists.stream().map(e->e.getDrugName()).collect(Collectors.toList());
            LOGGER.error("setDetailsInfo 存在无法一起配送的药品. recipeId=[{}], drugIds={}, noFilterDrugName={}",
                    recipeId, JSONUtils.toString(drugIds), JSONUtils.toString(noFilterDrugName));
            //一张处方单上的药品不能同时支持同一家药企配送
            //throw new DAOException(ErrorCode.SERVICE_ERROR, Joiner.on(",").join(noFilterDrugName) + "不能开具在一张处方上！");
            result.put("code", "609");
            result.put("msg", Joiner.on(",").join(drugNames) + "不支持同一家药企配送，建议拆分药品开方。");
        }
        return result;
    }*/

    /*private static Map<String, String> canOpenRecipeDrugsAndDiseaseCopy(RecipeBean recipe, List<Integer> drugIds) {
        Map<String, String> result = new HashMap<>();
        List<String> nameLists = Splitter.on("；").splitToList(recipe.getOrganDiseaseName());
        DrugListDAO drugListDAO = DAOFactory.getDAO(DrugListDAO.class);
        for (String organDiseaseName : nameLists){
            Set<String> drugIdSet = cacheService.findDrugByDiseaseName(recipe.getClinicOrgan()+"_"+organDiseaseName);
            if (CollectionUtils.isEmpty(drugIdSet)){continue;}
            for (String drugId:drugIdSet){
                if (drugIds.contains(Integer.valueOf(drugId))){
                    DrugList drugList = drugListDAO.getById(Integer.valueOf(drugId));
                    result.put("code", "609");
                    result.put("msg", "本处方中:"+drugList.getDrugName()+"对诊断为["+organDiseaseName+"]的患者禁用,请修改处方,如确认无误请联系管理员");
                }
            }
        }
        return result;
    }*/

    /**
     * 组装生成pdf的参数集合
     * zhongzx
     *
     * @param recipe  处方对象
     * @param details 处方详情
     * @return Map<String, Object>
     */
    public static Map<String, Object> createParamMap(Recipe recipe, List<Recipedetail> details, String fileName) {
        Map<String, Object> paramMap = Maps.newHashMap();
        try {
            PatientDTO p = patientService.get(recipe.getMpiid());
            if (null == p) {
                LOGGER.error("createParamMap 病人不存在. recipeId={}, mpiId={}", recipe.getRecipeId(), recipe.getMpiid());
                return paramMap;
            }
            //模板类型，西药模板
            paramMap.put("templateType", "wm");
            //生成pdf文件的入参
            paramMap.put("fileName", fileName);
            paramMap.put("recipeType", recipe.getRecipeType());
            String recipeType = DictionaryController.instance().get("eh.cdr.dictionary.RecipeType").getText(recipe.getRecipeType());
            paramMap.put("title", recipeType + "处方笺");
            paramMap.put("pName", p.getPatientName());
            paramMap.put("pGender", DictionaryController.instance().get("eh.base.dictionary.Gender").getText(p.getPatientSex()));
            paramMap.put("pAge", DateConversion.getAge(p.getBirthday()) + "岁");
            paramMap.put("pType", DictionaryController.instance().get("eh.mpi.dictionary.PatientType").getText(p.getPatientType()));
            paramMap.put("doctor", DictionaryController.instance().get("eh.base.dictionary.Doctor").getText(recipe.getDoctor()));
            String organ = DictionaryController.instance().get("eh.base.dictionary.Organ").getText(recipe.getClinicOrgan());
            String depart = DictionaryController.instance().get("eh.base.dictionary.Depart").getText(recipe.getDepart());
            paramMap.put("organInfo", organ);
            // 添加机构id
            paramMap.put("organId", recipe.getClinicOrgan());
            paramMap.put("departInfo", depart);
            paramMap.put("disease", recipe.getOrganDiseaseName());
            paramMap.put("cDate", DateConversion.getDateFormatter(recipe.getSignDate(), "yyyy-MM-dd HH:mm"));
            paramMap.put("diseaseMemo", recipe.getMemo());
            paramMap.put("recipeCode", recipe.getRecipeCode() == null? "" : recipe.getRecipeCode().startsWith("ngari") ? "" : recipe.getRecipeCode());
            paramMap.put("patientId", recipe.getPatientID());
            paramMap.put("mobile", p.getMobile());
            paramMap.put("loginId", p.getLoginId());
            paramMap.put("label", recipeType + "处方");
            int i = 0;
            ctd.dictionary.Dictionary usingRateDic = DictionaryController.instance().get("eh.cdr.dictionary.UsingRate");
            Dictionary usePathwaysDic = DictionaryController.instance().get("eh.cdr.dictionary.UsePathways");
            String useDose;
            for (Recipedetail d : details) {
                String dName = (i + 1) + "、" + d.getDrugName();
                //规格+药品单位
                String dSpec = d.getDrugSpec() + "/" + d.getDrugUnit();
                //使用天数
                String useDay = d.getUseDays() + "天";
                if (StringUtils.isNotEmpty(d.getUseDoseStr())){
                    useDose = d.getUseDoseStr();
                }else {
                    useDose = d.getUseDose() !=null?String.valueOf(d.getUseDose()):d.getUseDoseStr();
                }
                //每次剂量+剂量单位
                String uDose = "Sig: " + "每次" + useDose + (StringUtils.isEmpty(d.getUseDoseUnit()) ?
                        "" : d.getUseDoseUnit());
                //开药总量+药品单位
                String dTotal = "X" + d.getUseTotalDose() + d.getDrugUnit();
                //用药频次
                String dRateName = d.getUsingRate() + "(" + usingRateDic.getText(d.getUsingRate()) + ")";
                //用法
                String dWay = d.getUsePathways() + "(" + usePathwaysDic.getText(d.getUsePathways()) + ")";
                paramMap.put("drugInfo" + i, dName + dSpec);
                paramMap.put("dTotal" + i, dTotal);
                paramMap.put("useInfo" + i, uDose + "    " + dRateName + "    " + dWay + "    " + useDay);
                if (!StringUtils.isEmpty(d.getMemo())) {
                    //备注
                    paramMap.put("dMemo" + i, "备注:" + d.getMemo());
                }
                i++;
            }
            paramMap.put("drugNum", i);
        } catch (Exception e) {
            LOGGER.error("createParamMap 组装参数错误. recipeId={}, error ", recipe.getRecipeId(), e);
        }
        return paramMap;
    }

    /**
     * 中药处方pdf模板
     *
     * @param recipe
     * @param details
     * @param fileName
     * @return
     * @Author liuya
     */
    public static Map<String, Object> createParamMapForChineseMedicine(Recipe recipe, List<Recipedetail> details, String fileName) {
        Map<String, Object> paramMap = Maps.newHashMap();
        try {
            PatientDTO p = patientService.get(recipe.getMpiid());
            if (null == p) {
                LOGGER.error("createParamMapForChineseMedicine 病人不存在. recipeId={}, mpiId={}", recipe.getRecipeId(), recipe.getMpiid());
                return paramMap;
            }
            //模板类型，中药类模板
            paramMap.put("templateType", "tcm");
            //生成pdf文件的入参
            paramMap.put("fileName", fileName);
            paramMap.put("recipeType", recipe.getRecipeType());
            String recipeType = DictionaryController.instance().get("eh.cdr.dictionary.RecipeType").getText(recipe.getRecipeType());
            paramMap.put("title", recipeType + "处方笺");
            paramMap.put("pName", p.getPatientName());
            paramMap.put("pGender", DictionaryController.instance().get("eh.base.dictionary.Gender").getText(p.getPatientSex()));
            paramMap.put("pAge", DateConversion.getAge(p.getBirthday()) + "岁");
            paramMap.put("pType", DictionaryController.instance().get("eh.mpi.dictionary.PatientType").getText(p.getPatientType()));
            paramMap.put("doctor", DictionaryController.instance().get("eh.base.dictionary.Doctor").getText(recipe.getDoctor()));
            String organ = DictionaryController.instance().get("eh.base.dictionary.Organ").getText(recipe.getClinicOrgan());
            String depart = DictionaryController.instance().get("eh.base.dictionary.Depart").getText(recipe.getDepart());
            paramMap.put("organInfo", organ);
            // 添加机构id
            paramMap.put("organId", recipe.getClinicOrgan());
            paramMap.put("departInfo", depart);
            paramMap.put("disease", recipe.getOrganDiseaseName());
            paramMap.put("cDate", DateConversion.getDateFormatter(recipe.getSignDate(), "yyyy-MM-dd HH:mm"));
            paramMap.put("diseaseMemo", recipe.getMemo());
            paramMap.put("recipeCode", recipe.getRecipeCode() == null? "" : recipe.getRecipeCode().startsWith("ngari") ? "" : recipe.getRecipeCode());
            paramMap.put("patientId", recipe.getPatientID());
            paramMap.put("mobile", p.getMobile());
            paramMap.put("loginId", p.getLoginId());
            paramMap.put("label", recipeType + "处方");
            paramMap.put("copyNum", recipe.getCopyNum() + "剂");
            paramMap.put("recipeMemo", recipe.getRecipeMemo());
            int i = 0;
            for (Recipedetail d : details) {
                String dName = d.getDrugName();
                //开药总量+药品单位
                String dTotal = "";
                if (StringUtils.isNotEmpty(d.getUseDoseStr())){
                    dTotal = d.getUseDoseStr()+d.getUseDoseUnit();
                }else {
                    if (d.getUseDose()!=null){
                        //增加判断条件  如果用量小数位为零，则不显示小数点
                        if ((d.getUseDose() - d.getUseDose().intValue()) == 0d) {
                            dTotal = d.getUseDose().intValue() + d.getUseDoseUnit();
                        } else {
                            dTotal = d.getUseDose() + d.getUseDoseUnit();
                        }
                    }
                }

                if (!StringUtils.isEmpty(d.getMemo())) {
                    //备注
                    dTotal = dTotal + "*" + d.getMemo();
                }
                paramMap.put("drugInfo" + i, dName + "¨" + dTotal);
                paramMap.put("tcmUsePathways", d.getUsePathways());
                paramMap.put("tcmUsingRate", d.getUsingRate());
                i++;
            }
            paramMap.put("drugNum", i);
        } catch (Exception e) {
            LOGGER.error("createParamMapForChineseMedicine 组装参数错误. recipeId={}, error ", recipe.getRecipeId(), e);
        }
        return paramMap;
    }

    /**
     * 处方列表服务
     *
     * @param doctorId 开方医生
     * @param start    分页开始位置
     * @param limit    每页限制条数
     * @param mark     标志 --0新处方1历史处方
     * @return List
     */
    public static List<HashMap<String, Object>> findRecipesAndPatientsByDoctor(
            final int doctorId, final int start, final int limit, final int mark) {
        if (0 == limit) {
            return null;
        }

        List<Recipe> recipes;
        // 是否含有未签名的数据
        boolean hasUnsignRecipe = false;
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        RecipeOrderDAO orderDAO = DAOFactory.getDAO(RecipeOrderDAO.class);

        if (0 == mark) {
            recipes = new ArrayList<>(0);
            int endIndex = start + limit;
            //先查询未签名处方的数量
            int unsignCount = recipeDAO.getCountByDoctorIdAndStatus(doctorId,
                    Arrays.asList(RecipeStatusConstant.CHECK_NOT_PASS, RecipeStatusConstant.UNSIGN), ConditionOperator.IN, false);
            //查询未签名的处方数据
            if (unsignCount > start) {
                hasUnsignRecipe = true;
                List<Recipe> unsignRecipes = recipeDAO.findByDoctorIdAndStatus(doctorId,
                        Arrays.asList(RecipeStatusConstant.CHECK_NOT_PASS, RecipeStatusConstant.UNSIGN), ConditionOperator.IN, false, start, limit, mark);
                if (null != unsignRecipes && !unsignRecipes.isEmpty()) {
                    recipes.addAll(unsignRecipes);
                }

                //当前页的数据未签名的数据无法充满则需要查询未审核的数据
                if (unsignCount < endIndex) {
                    List<Recipe> uncheckRecipes = recipeDAO.findByDoctorIdAndStatus(doctorId,
                            Collections.singletonList(RecipeStatusConstant.UNCHECK), ConditionOperator.EQUAL, false, 0, limit - recipes.size(), mark);
                    if (null != uncheckRecipes && !uncheckRecipes.isEmpty()) {
                        recipes.addAll(uncheckRecipes);
                    }
                }
            } else {
                //未签名的数据已经全部显示
                int startIndex = start - unsignCount;
                List<Recipe> uncheckRecipes = recipeDAO.findByDoctorIdAndStatus(doctorId,
                        Collections.singletonList(RecipeStatusConstant.UNCHECK), ConditionOperator.EQUAL, false, startIndex, limit, mark);
                if (null != uncheckRecipes && !uncheckRecipes.isEmpty()) {
                    recipes.addAll(uncheckRecipes);
                }
            }
        } else {
            //历史处方数据
            recipes = recipeDAO.findByDoctorIdAndStatus(doctorId,
                    Collections.singletonList(RecipeStatusConstant.CHECK_PASS), ConditionOperator.GREAT_EQUAL, false, start, limit, mark);
        }

        List<String> patientIds = new ArrayList<>(0);
        Map<Integer, RecipeBean> recipeMap = Maps.newHashMap();
        RecipeDetailDAO recipeDetailDAO = DAOFactory.getDAO(RecipeDetailDAO.class);
        //date 20200506
        //获取处方对应的订单信息
        Map<String, Integer> orderStatus = new HashMap<>();
        List<String> recipeCodes = recipes.stream().map(recipe -> recipe.getOrderCode()).filter(code -> StringUtils.isNotEmpty(code)).collect(Collectors.toList());
        if(CollectionUtils.isNotEmpty(recipeCodes)){
            List<RecipeOrder> recipeOrders = orderDAO.findValidListbyCodes(recipeCodes);
            orderStatus = recipeOrders.stream().collect(Collectors.toMap(RecipeOrder::getOrderCode, RecipeOrder::getStatus));
        }
        for (Recipe recipe : recipes) {
            if (StringUtils.isNotEmpty(recipe.getMpiid())) {
                patientIds.add(recipe.getMpiid());
            }
            //设置处方具体药品名称
            recipe.setRecipeDrugName(recipeDetailDAO.getDrugNamesByRecipeId(recipe.getRecipeId()));
            //前台页面展示的时间源不同
            if (0 == mark) {
                if (null != recipe.getLastModify()) {
                    recipe.setRecipeShowTime(recipe.getLastModify());
                }
            } else {
                if (null != recipe.getSignDate()) {
                    recipe.setRecipeShowTime(recipe.getSignDate());
                }
            }
            boolean effective = false;
            //只有审核未通过的情况需要看订单状态
            if (RecipeStatusConstant.CHECK_NOT_PASS_YS == recipe.getStatus()) {
                effective = orderDAO.isEffectiveOrder(recipe.getOrderCode(), recipe.getPayMode());
            }
            Map<String, String> tipMap = getTipsByStatusCopy(recipe.getStatus(), recipe, effective, (orderStatus == null || 0 >= orderStatus.size()) ? null : orderStatus.get(recipe.getOrderCode()));
            recipe.setShowTip(MapValueUtil.getString(tipMap, "listTips"));
            recipeMap.put(recipe.getRecipeId(), convertRecipeForRAP(recipe));
        }

        List<PatientDTO> patientList = null;
        if (!patientIds.isEmpty()) {
            patientList = patientService.findByMpiIdIn(patientIds);
        }
        Map<String, PatientDTO> patientMap = Maps.newHashMap();
        if (null != patientList && !patientList.isEmpty()) {
            for (PatientDTO patient : patientList) {
                //设置患者数据
                setPatientMoreInfo(patient, doctorId);
                patientMap.put(patient.getMpiId(), convertPatientForRAP(patient));
            }
        }

        List<HashMap<String, Object>> list = new ArrayList<>(0);
        List<HashMap<String, Object>> unsignMapList = new ArrayList<>(0);
        List<HashMap<String, Object>> uncheckMapList = new ArrayList<>(0);
        for (Recipe recipe : recipes) {
            //对处方数据进行分类
            String mpiid = recipe.getMpiid();
            HashMap<String, Object> map = Maps.newHashMap();
            map.put("recipe", recipeMap.get(recipe.getRecipeId()));
            map.put("patient", patientMap.get(mpiid));

            //新开处方与历史处方JSON结构不同
            if (0 == mark) {
                if (hasUnsignRecipe) {
                    if (recipe.getStatus() <= RecipeStatusConstant.UNSIGN) {
                        //未签名处方
                        unsignMapList.add(map);
                    } else if (RecipeStatusConstant.UNCHECK == recipe.getStatus()) {
                        //未审核处方
                        uncheckMapList.add(map);
                    }
                } else {
                    uncheckMapList.add(map);
                }
            } else {
                list.add(map);
            }
        }

        if (!unsignMapList.isEmpty()) {
            HashMap<String, Object> map = Maps.newHashMap();
            map.put(UNSIGN, unsignMapList);
            list.add(map);
        }

        if (!uncheckMapList.isEmpty()) {
            HashMap<String, Object> map = Maps.newHashMap();
            map.put(UNCHECK, uncheckMapList);
            list.add(map);
        }

        return list;
    }

    /**
     * 状态文字提示（医生端）
     *
     * @param status
     * @param recipe
     * @param effective
     * @return
     */
    public static Map<String, String> getTipsByStatus(int status, Recipe recipe, boolean effective) {
        String cancelReason = "";
        String tips = "";
        String listTips = "";
        switch (status) {
            case RecipeStatusConstant.CHECK_NOT_PASS:
                tips = "审核未通过";
                break;
            case RecipeStatusConstant.UNSIGN:
                tips = "未签名";
                break;
            case RecipeStatusConstant.UNCHECK:
                tips = "待审核";
                break;
            case RecipeStatusConstant.CHECK_PASS:
                tips = "待处理";
                break;
            case RecipeStatusConstant.REVOKE:
                tips = "已取消";
                cancelReason = "由于您已撤销，该处方单已失效";
                break;
            case RecipeStatusConstant.HAVE_PAY:
                tips = "待取药";
                break;
            case RecipeStatusConstant.IN_SEND:
                tips = "配送中";
                break;
            case RecipeStatusConstant.WAIT_SEND:
                tips = "待配送";
                break;
            case RecipeStatusConstant.FINISH:
                tips = "已完成";
                break;
            case RecipeStatusConstant.CHECK_PASS_YS:
                if (StringUtils.isNotEmpty(recipe.getSupplementaryMemo())) {
                    tips = "医生再次确认处方";
                } else {
                    tips = "审核通过";
                }
                listTips = "审核通过";
                break;
            case RecipeStatusConstant.READY_CHECK_YS:
                tips = "待审核";
                break;
            case RecipeStatusConstant.HIS_FAIL:
                tips = "已取消";
                cancelReason = "可能由于医院接口异常，处方单已取消，请稍后重试！";
                break;
            case RecipeStatusConstant.NO_DRUG:
                tips = "已取消";
                cancelReason = "由于患者未及时取药，该处方单已失效";
                break;
            case RecipeStatusConstant.NO_PAY:
                tips = "已取消";
                cancelReason = "由于患者未及时支付，该处方单已取消。";
                break;
            case RecipeStatusConstant.NO_OPERATOR:
                tips = "已取消";
                cancelReason = "由于患者未及时处理，该处方单已取消。";
                break;
            case RecipeStatusConstant.CHECK_NOT_PASS_YS:
                if (recipe.canMedicalPay()) {
                    tips = "审核未通过";
                } else {
                    if (effective) {
                        tips = "审核未通过";
                    } else {
                        tips = "已取消";
                    }
                }
                break;
            case RecipeStatusConstant.CHECKING_HOS:
                tips = "医院确认中";
                break;
            //天猫特殊状态
            case RecipeStatusConstant.USING:
                tips = "处理中";
                break;
            default:
                tips = "未知状态" + status;
        }
        if (StringUtils.isEmpty(listTips)) {
            listTips = tips;
        }
        Map<String, String> map = Maps.newHashMap();
        map.put("tips", tips);
        map.put("listTips", listTips);
        map.put("cancelReason", cancelReason);
        return map;
    }

    /**
     * 状态文字提示（医生端）
     *
     * @param status
     * @param recipe
     * @param effective
     * @return
     */
    public static Map<String, String> getTipsByStatusCopy(int status, Recipe recipe, Boolean effective, Integer orderStatus) {
        RecipeLogDAO recipeLogDAO = DAOFactory.getDAO(RecipeLogDAO.class);
        String cancelReason = "";
        String tips = "";
        String listTips = "";
        List<RecipeLog> recipeLog = null;

        //date 20200515
        //下载处方，下载处方待取药特殊处理，根据处方状态来
        switch (status) {
            case RecipeStatusConstant.RECIPE_DOWNLOADED:
                tips = "待取药";
                break;
        }

        //date 20200506
        //修改展示状态的方式，有订单的状态现优先展示订单的状态再展示处方的状态
        if(StringUtils.isEmpty(tips)) {
            if (null != orderStatus) {
                switch (orderStatus) {
                    case OrderStatusConstant.READY_GET_DRUG_INT:
                        if (null != recipe.getGiveMode() && RecipeBussConstant.GIVEMODE_DOWNLOAD_RECIPE.equals(recipe.getGiveMode())) {
                            tips = "待下载";
                        } else {
                            tips = "待取药";
                        }
                        break;
                    case OrderStatusConstant.READY_DRUG_INT:
                        tips = "待取药";
                        break;
                    case OrderStatusConstant.READY_SEND_INT:
                        tips = "待配送";
                        break;
                    case OrderStatusConstant.SENDING_INT:
                        tips = "配送中";
                        break;
                    //date 20200514
                    //添加订单药店配送有无库存待取药
                    case OrderStatusConstant.NO_DRUG_INT:
                        tips = "待取药";
                        break;
                    case OrderStatusConstant.HAS_DRUG_INT:
                        tips = "待取药";
                        break;
                }
            }
        }
        //date 20200506
        //判断当订单的状态不存在的时候用处方的状态
        if(StringUtils.isEmpty(tips)){

            switch (status) {
                case RecipeStatusConstant.CHECK_NOT_PASS:
                    tips = "审核未通过";
                    break;
                case RecipeStatusConstant.UNSIGN:
                    tips = "未签名";
                    break;
                case RecipeStatusConstant.UNCHECK:
                    tips = "待审核";
                    break;
                case RecipeStatusConstant.CHECK_PASS:
                    tips = "待处理";
                    break;
                case RecipeStatusConstant.REVOKE:
                    tips = "已撤销";
                    cancelReason = "由于您已撤销，该处方单已失效";
                    List<RecipeLog> recipeLogs = recipeLogDAO.findByRecipeIdAndAfterStatus(recipe.getRecipeId(), RecipeStatusConstant.REVOKE);
                    if (CollectionUtils.isNotEmpty(recipeLogs)) {
                        cancelReason = recipeLogs.get(0).getMemo();
                    }
                    break;
                case RecipeStatusConstant.HAVE_PAY:
                    tips = "待取药";
                    break;
                case RecipeStatusConstant.IN_SEND:
                    tips = "配送中";
                    break;
                case RecipeStatusConstant.WAIT_SEND:
                    tips = "待配送";
                    break;
                case RecipeStatusConstant.FINISH:
                    tips = "已完成";
                    break;
                case RecipeStatusConstant.CHECK_PASS_YS:
                    if (StringUtils.isNotEmpty(recipe.getSupplementaryMemo())) {
                        tips = "医生再次确认处方";
                    } else {
                        tips = "审核通过";
                    }
                    listTips = "审核通过";
                    break;
                case RecipeStatusConstant.READY_CHECK_YS:
                    tips = "待审核";
                    break;
                case RecipeStatusConstant.HIS_FAIL:
                    tips = "已取消";
                    //date 20200507
                    //判断当前处方调用医院接口是否有异常信息，有的话展示异常信息抹油获取默认信息
                    List<RecipeLog> recipeFailLogs = recipeLogDAO.findByRecipeIdAndAfterStatusDesc(recipe.getRecipeId(), RecipeStatusConstant.HIS_FAIL);
                    if(CollectionUtils.isNotEmpty(recipeFailLogs)){
                        cancelReason = recipeFailLogs.get(0).getMemo().substring(recipeFailLogs.get(0).getMemo().indexOf("|") + 1, recipeFailLogs.get(0).getMemo().length() - 1);
                    }else{

                        cancelReason = "可能由于医院接口异常，处方单已取消，请稍后重试！";
                    }
                    break;
                case RecipeStatusConstant.NO_DRUG:
                    tips = "已取消";
                    cancelReason = "由于患者未及时取药，该处方单已失效";
                    break;
                case RecipeStatusConstant.NO_PAY:
                    //修改文案
                    tips = "已取消";
                    cancelReason = "由于患者未及时支付，该处方单已取消。";
                    break;
                case RecipeStatusConstant.NO_OPERATOR:
                    //修改文案
                    tips = "已取消";
                    cancelReason = "由于患者未及时处理，该处方已取消。";
                    break;
                case RecipeStatusConstant.NO_MEDICAL_INSURANCE_RETURN:
                    tips = "已取消";
                    cancelReason = "处方超时医保上传失败，处方单已取消！";
                    break;
                case RecipeStatusConstant.RECIPE_MEDICAL_FAIL:
                    tips = "已取消";
                    cancelReason = "医保上传失败，处方单已取消！";
                    break;
                case RecipeStatusConstant.CHECK_NOT_PASS_YS:
                    //这里逻辑修改：原先处方取消后，保留处方的状态
                    //现在初始化成待处理的状态
                    //date 20200506
                    //修改审核不通过文案修改
                    tips = "审核不通过";

                    break;
                case RecipeStatusConstant.CHECKING_HOS:
                    tips = "医院确认中";
                    break;
                //添加状态
                case RecipeStatusConstant.RECIPE_FAIL:
                    tips = "失败";
                    break;
                case RecipeStatusConstant.RECIPE_DOWNLOADED:
                    tips = "待取药";
                    break;
                //天猫特殊状态
                case RecipeStatusConstant.USING:
                    tips = "处理中";
                    break;
                case RecipeStatusConstant.CHECKING_MEDICAL_INSURANCE:
                    tips = "医保上传确认中";
                    break;
                case RecipeStatusConstant.SIGN_ERROR_CODE_DOC:
                    recipeLog = recipeLogDAO.findByRecipeIdAndAfterStatus(recipe.getRecipeId(), RecipeStatusConstant.SIGN_ERROR_CODE_DOC);
                    tips = "处方签名失败";
                    if (recipeLog != null &&recipeLog.size() > 0) {
                        cancelReason = "处方签名失败:" + recipeLog.get(0).getMemo();
                    }else{
                        cancelReason = "处方签名失败！";
                    }
                    break;
                case RecipeStatusConstant.SIGN_ERROR_CODE_PHA:
                    recipeLog = recipeLogDAO.findByRecipeIdAndAfterStatus(recipe.getRecipeId(), RecipeStatusConstant.SIGN_ERROR_CODE_PHA);
                    tips = "审方签名失败";
                    if (recipeLog != null &&recipeLog.size() > 0) {
                        cancelReason = "审方签名失败:" + recipeLog.get(0).getMemo();
                    }else{
                        cancelReason = "审方签名失败！";
                    }
                    break;
                case RecipeStatusConstant.SIGN_ING_CODE_DOC:
                    tips = "处方签名中";
                    break;
                case RecipeStatusConstant.SIGN_ING_CODE_PHA:
                    tips = "审方签名中";
                    break;
                default:
                    tips = "未知状态" + status;
            }
        }
        if (StringUtils.isEmpty(listTips)) {
            listTips = tips;
        }
        Map<String, String> map = Maps.newHashMap();
        map.put("tips", tips);
        map.put("listTips", listTips);
        map.put("cancelReason", cancelReason);
        return map;
    }

    public static void setPatientMoreInfo(PatientDTO patient, int doctorId) {
        IRelationPatientService iRelationPatientService= AppContextHolder.getBean("pm.remoteRelationPatientService", IRelationPatientService.class);
        RelationDoctorVO relationDoctor = iRelationPatientService.getByMpiidAndDoctorId(patient.getMpiId(), doctorId);
        /*RelationDoctorBean relationDoctor = doctorService.getByMpiidAndDoctorId(patient.getMpiId(), doctorId);*/
        //是否关注
        Boolean relationFlag = false;
        //是否签约
        Boolean signFlag = false;
        List<String> labelNames = Lists.newArrayList();
        if (relationDoctor != null) {
            relationFlag = true;
            if (relationDoctor.getFamilyDoctorFlag()) {
                signFlag = true;
            }

            labelNames = patientService.findLabelNamesByRPId(relationDoctor.getRelationDoctorId());

        }
        patient.setRelationFlag(relationFlag);
        patient.setSignFlag(signFlag);
        patient.setLabelNames(labelNames);
    }

    public static PatientDTO convertPatientForRAP(PatientDTO patient) {
        PatientDTO p = new PatientDTO();
        p.setPatientName(patient.getPatientName());
        p.setPatientSex(patient.getPatientSex());
        p.setBirthday(patient.getBirthday());
        p.setPatientType(patient.getPatientType());
        p.setIdcard(patient.getCertificate());
        p.setStatus(patient.getStatus());
//        p.setMobile(patient.getMobile());
        p.setMpiId(patient.getMpiId());
        p.setPhoto(patient.getPhoto());
        p.setSignFlag(patient.getSignFlag());
        p.setRelationFlag(patient.getRelationFlag());
        p.setLabelNames(patient.getLabelNames());
        p.setGuardianFlag(patient.getGuardianFlag());
        p.setGuardianCertificate(patient.getGuardianCertificate());
        p.setGuardianName(patient.getGuardianName());
        p.setAge(null == patient.getBirthday() ? 0 : DateConversion.getAge(patient.getBirthday()));
        return p;
    }

    public static RecipeBean convertRecipeForRAP(Recipe recipe) {
        RecipeBean r = new RecipeBean();
        r.setRecipeId(recipe.getRecipeId());
        r.setCreateDate(recipe.getCreateDate());
        r.setRecipeType(recipe.getRecipeType());
        r.setStatus(recipe.getStatus());
        r.setOrganDiseaseName(recipe.getOrganDiseaseName());
        r.setRecipeDrugName(recipe.getRecipeDrugName());
        r.setRecipeShowTime(recipe.getRecipeShowTime());
        r.setShowTip(recipe.getShowTip());
        return r;
    }

    /**
     * 获取处方详情
     *
     * @param recipeId
     * @param isDoctor true:医生端  false:健康端
     * @return
     */
    public static Map<String, Object> getRecipeAndDetailByIdImpl(int recipeId, boolean isDoctor) {
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        RecipeOrderDAO orderDAO = DAOFactory.getDAO(RecipeOrderDAO.class);
        RecipeOrderDAO recipeOrderDAO = DAOFactory.getDAO(RecipeOrderDAO.class);

        Recipe recipe = recipeDAO.getByRecipeId(recipeId);
        Map<String, Object> map = Maps.newHashMap();
        if (recipe == null) {
            return map;
        }

        DrugsEnterpriseService drugsEnterpriseService = ApplicationUtils.getRecipeService(DrugsEnterpriseService.class);
        map.put("checkEnterprise", drugsEnterpriseService.checkEnterprise(recipe.getClinicOrgan()));
        RecipeDetailDAO detailDAO = DAOFactory.getDAO(RecipeDetailDAO.class);
        PatientDTO patientBean = patientService.get(recipe.getMpiid());
        PatientDTO patient = null;
        if (patientBean != null) {
            //添加患者标签和关注这些字段
            RecipeServiceSub.setPatientMoreInfo(patientBean, recipe.getDoctor());
            patient = RecipeServiceSub.convertPatientForRAP(patientBean);
            //判断该就诊人是否为儿童就诊人
            if (patient.getAge() <= 5 && !ObjectUtils.isEmpty(patient.getGuardianCertificate())) {
                GuardianBean guardian = new GuardianBean();
                guardian.setName(patient.getGuardianName());
                try{
                    guardian.setAge(ChinaIDNumberUtil.getAgeFromIDNumber(patient.getGuardianCertificate()));
                    guardian.setSex(ChinaIDNumberUtil.getSexFromIDNumber(patient.getGuardianCertificate()));
                } catch (ValidateException exception) {
                    LOGGER.warn("监护人使用身份证号获取年龄或者性别出错.{}.", exception.getMessage());
                }
                map.put("guardian", guardian);
            }
            if (!ObjectUtils.isEmpty(patient.getGuardianCertificate())) {
                //对监护人信息进行脱敏处理
                patient.setGuardianCertificate(ChinaIDNumberUtil.hideIdCard(patient.getGuardianCertificate()));
            }
        }
        List<Recipedetail> recipedetails = detailDAO.findByRecipeId(recipeId);
        OrganDrugListDAO organDrugListDAO = DAOFactory.getDAO(OrganDrugListDAO.class);

        try{
            for (Recipedetail recipedetail : recipedetails) {
                List<OrganDrugList> organDrugLists = organDrugListDAO.findByDrugIdAndOrganId(recipedetail.getDrugId(), recipe.getClinicOrgan());
                if (CollectionUtils.isNotEmpty(organDrugLists)) {
                    recipedetail.setDrugForm(organDrugLists.get(0).getDrugForm());
                }

                //处方来源：线下 从his获取用药频率说明、用药方式说明
//                if(recipe.getRecipeSourceType()==2){
//                    HisRecipeDetailDAO hisRecipeDetailDAO = DAOFactory.getDAO(HisRecipeDetailDAO.class);
//                    List<HisRecipeDetail> hisRecipeDetails = hisRecipeDetailDAO.findByHisRecipeId(recipeId);
//                    for (HisRecipeDetail hisRecipeDetail : hisRecipeDetails) {
//                        if(!StringUtils.isEmpty(hisRecipeDetail.getDrugCode())
//                                &&hisRecipeDetail.getDrugCode().equals(recipedetail.getDrugCode())){
//                            recipedetail.setUsingRateTextFromHis(hisRecipeDetail.getUsingRateText());
//                            recipedetail.setUsePathwaysTextFromHis(hisRecipeDetail.getUsePathwaysText());
//                            break;
//                        }
//                    }
//                }


            }
        }catch(Exception e){
            LOGGER.info("RecipeServiceSub.getRecipeAndDetailByIdImpl 查询剂型出错, recipeId:{},{}.", recipeId, e.getMessage());
        }

        //中药处方处理
        if (RecipeBussConstant.RECIPETYPE_TCM.equals(recipe.getRecipeType())) {
            if (CollectionUtils.isNotEmpty(recipedetails)) {
                Recipedetail recipedetail = recipedetails.get(0);
                recipe.setTcmUsePathways(recipedetail.getUsePathways());
                recipe.setTcmUsingRate(recipedetail.getUsingRate());
            }
        }
        map.put("patient", patient);
        map.put("recipedetails", ObjectCopyUtils.convert(recipedetails, RecipeDetailBean.class));
        if (isDoctor) {
            ConsultSetService consultSetService = ApplicationUtils.getBasicService(ConsultSetService.class);
            IOrganConfigService iOrganConfigService = ApplicationUtils.getBaseService(IOrganConfigService.class);

            // 获取处方单药品总价
            RecipeUtil.getRecipeTotalPriceRange(recipe, recipedetails);
            boolean effective = orderDAO.isEffectiveOrder(recipe.getOrderCode(), recipe.getPayMode());

            //date 20200506
            //通过订单的状态判断
            Map<String, Integer> orderStatus = new HashMap<>();
            if(null != recipe.getRecipeCode()){
                List<RecipeOrder> recipeOrders = orderDAO.findValidListbyCodes(Arrays.asList(recipe.getOrderCode()));
                orderStatus = recipeOrders.stream().collect(Collectors.toMap(RecipeOrder::getOrderCode, RecipeOrder::getStatus));
            }
            Map<String, String> tipMap = RecipeServiceSub.getTipsByStatusCopy(recipe.getStatus(), recipe, null, (orderStatus == null || 0 >= orderStatus.size()) ? null : orderStatus.get(recipe.getOrderCode()));
            map.put("cancelReason", MapValueUtil.getString(tipMap, "cancelReason"));
            map.put("tips", MapValueUtil.getString(tipMap, "tips"));

            RecipeCheckService service = ApplicationUtils.getRecipeService(RecipeCheckService.class);
            //获取审核不通过详情
            List<Map<String, Object>> mapList = service.getCheckNotPassDetail(recipeId);
            map.put("reasonAndDetails", mapList);

            //设置处方撤销标识 true:可以撤销, false:不可撤销
            Boolean cancelFlag = false;
            if (RecipeStatusConstant.REVOKE != recipe.getStatus()) {
                if (!Integer.valueOf(1).equals(recipe.getPayFlag())
                        && recipe.getStatus() != RecipeStatusConstant.UNSIGN
                        && recipe.getStatus() != RecipeStatusConstant.HIS_FAIL
                        && recipe.getStatus() != RecipeStatusConstant.NO_DRUG
                        && recipe.getStatus() != RecipeStatusConstant.NO_PAY
                        && recipe.getStatus() != RecipeStatusConstant.NO_OPERATOR
                        && recipe.getStatus() != RecipeStatusConstant.RECIPE_MEDICAL_FAIL
                        && recipe.getStatus() != RecipeStatusConstant.CHECKING_HOS
                        && recipe.getStatus() != RecipeStatusConstant.NO_MEDICAL_INSURANCE_RETURN
                        //date 2020/05/14
                        //将签名失败和审核失败的
                        && recipe.getStatus() != RecipeStatusConstant.SIGN_ERROR_CODE_PHA
                        && recipe.getStatus() != RecipeStatusConstant.SIGN_ERROR_CODE_DOC
                        && recipe.getStatus() != RecipeStatusConstant.SIGN_ING_CODE_DOC
                        && recipe.getStatus() != RecipeStatusConstant.SIGN_ING_CODE_PHA
                        && !Integer.valueOf(1).equals(recipe.getChooseFlag())) {
                    cancelFlag = true;
                }
            }
            map.put("cancelFlag", cancelFlag);
            //能否开医保处方
            boolean medicalFlag = false;
            ConsultSetDTO set = consultSetService.getBeanByDoctorId(recipe.getDoctor());
            if (null != set && null != set.getMedicarePrescription()) {
                medicalFlag = (true == set.getMedicarePrescription()) ? true : false;
            }
            map.put("medicalFlag", medicalFlag);
            if (null != recipe.getChecker() && recipe.getChecker() > 0) {
                String ysTel = doctorService.getMobileByDoctorId(recipe.getChecker());
                if (StringUtils.isNotEmpty(ysTel)) {
                    recipe.setCheckerTel(ysTel);
                }
            }

            //审核不通过处方单详情增加二次签名标记
            //date 20191011
            //添加一次审核不通过标志位,取消之前通过订单是否有效的判断
            //boolean b = RecipeStatusConstant.CHECK_NOT_PASS_YS == recipe.getStatus() && (recipe.canMedicalPay() || effective);
            boolean b = RecipeStatusConstant.CHECK_NOT_PASS_YS == recipe.getStatus() && (recipe.canMedicalPay() || (RecipecCheckStatusConstant.First_Check_No_Pass == recipe.getCheckStatus()));
            if (b) {
                map.put("secondSignFlag", canSecondAudit(recipe.getClinicOrgan()));
            }

            //增加医生返回智能审方结果药品问题列表 2018.11.26 shiyp
            //判断开关是否开启
            PrescriptionService prescriptionService = ApplicationUtils.getRecipeService(PrescriptionService.class);
            if (prescriptionService.getIntellectJudicialFlag(recipe.getClinicOrgan()) == 1) {
                List<AuditMedicinesDTO> auditMedicines = getAuditMedicineIssuesByRecipeId(recipeId);
                map.put("medicines", getAuditMedicineIssuesByRecipeId(recipeId)); //返回药品分析数据
                AuditMedicineIssueDAO auditMedicineIssueDAO = DAOFactory.getDAO(AuditMedicineIssueDAO.class);
                List<AuditMedicineIssue> auditMedicineIssues = auditMedicineIssueDAO.findIssueByRecipeId(recipeId);
                if(CollectionUtils.isNotEmpty(auditMedicineIssues)){
                    List<AuditMedicineIssue> resultMedicineIssues = new ArrayList<>();
                    auditMedicineIssues.forEach(item->{
                        if (null == item.getMedicineId()) {
                            resultMedicineIssues.add(item);
                        }
                    });

                    List<PAWebRecipeDanger> recipeDangers = new ArrayList<>();
                    resultMedicineIssues.forEach(item->{
                        PAWebRecipeDanger recipeDanger = new PAWebRecipeDanger();
                        recipeDanger.setDangerDesc(item.getDetail());
                        recipeDanger.setDangerDrug(item.getTitle());
                        recipeDanger.setDangerLevel(item.getLvlCode());
                        recipeDanger.setDangerType(item.getLvl());
                        recipeDanger.setDetailUrl(item.getDetailUrl());
                        recipeDangers.add(recipeDanger);
                    });
                    map.put("recipeDangers",recipeDangers); //返回处方分析数据
                }
            }

        } else {
            //处方详情单底部文案提示说明---机构配置
            map.put("bottomText", getBottomTextForPatient(recipe.getClinicOrgan()));
            RecipeOrder order = orderDAO.getOrderByRecipeId(recipeId);
            if (recipe.getRecipeMode() == RecipeBussConstant.RECIPEMODE_ZJJGPT) {
                map.put("tips", getTipsByStatusForPatient(recipe, order));
            } else {
                PurchaseService purchaseService = ApplicationUtils.getRecipeService(PurchaseService.class);
                map.put("tips", purchaseService.getTipsByStatusForPatient(recipe, order));
            }
            //获取医生撤销原因
            if (recipe.getStatus() == RecipeStatusConstant.REVOKE){
                map.put("cancelReason", getCancelReasonForPatient(recipeId));
            }
            boolean b = null != recipe.getEnterpriseId() && RecipeBussConstant.GIVEMODE_SEND_TO_HOME.equals(recipe.getGiveMode())
                    && (recipe.getStatus() == RecipeStatusConstant.WAIT_SEND || recipe.getStatus() == RecipeStatusConstant.IN_SEND
                    || recipe.getStatus() == RecipeStatusConstant.FINISH);
            if (b) {
                DrugsEnterpriseDAO drugsEnterpriseDAO = DAOFactory.getDAO(DrugsEnterpriseDAO.class);
                map.put("depTel", drugsEnterpriseDAO.getTelById(recipe.getEnterpriseId()));
            }

            recipe.setOrderAmount(recipe.getTotalMoney());
            BigDecimal actualPrice = null;
            if (null != order) {
                actualPrice = order.getRecipeFee();
                recipe.setDiscountAmount(order.getCouponName());
            } else {
                // couponId = -1有优惠券  不使用 显示“不使用优惠券”
                actualPrice = recipe.getActualPrice();
                recipe.setDiscountAmount("0.0");

                //如果获取不到有效订单，则不返回订单编号（场景：医保消息发送成功后，处方单关联了一张无效订单，此时处方单点击自费结算，应跳转到订单确认页面）
                recipe.setOrderCode(null);
            }
            if (null == actualPrice) {
                actualPrice = recipe.getTotalMoney();
            }
            recipe.setActualPrice(actualPrice);

            //无法配送时间文案提示
            map.put("unSendTitle", getUnSendTitleForPatient(recipe));
            //患者处方取药方式提示
            map.put("recipeGetModeTip", getRecipeGetModeTip(recipe));

            if (null != order && 1 == order.getEffective() && StringUtils.isNotEmpty(recipe.getOrderCode())) {
                //如果创建过自费订单，则不显示医保支付
                recipe.setMedicalPayFlag(0);
            }

            //药品价格显示处理
            //date 2020/1/2
            //展示修改成处方都展示药品金额
//            boolean b1 = RecipeStatusConstant.FINISH == recipe.getStatus() ||
//                    (1 == recipe.getChooseFlag() && !RecipeUtil.isCanncelRecipe(recipe.getStatus()) &&
//                            (RecipeBussConstant.PAYMODE_MEDICAL_INSURANCE.equals(recipe.getPayMode())
//                                    || RecipeBussConstant.PAYMODE_ONLINE.equals(recipe.getPayMode())
//                                    || RecipeBussConstant.PAYMODE_TO_HOS.equals(recipe.getPayMode())));
//            if (!b1) {
//                recipe.setTotalMoney(null);
//            }

            //Date:20190904
            //Explain:审核是否通过
            boolean isOptional = !(ReviewTypeConstant.Preposition_Check == recipe.getReviewType() &&
                    (RecipeStatusConstant.READY_CHECK_YS == recipe.getStatus() || (RecipeStatusConstant.CHECK_NOT_PASS_YS == recipe.getStatus() && RecipecCheckStatusConstant.First_Check_No_Pass == recipe.getCheckStatus())));
            map.put("optional", isOptional);

            //date 2190929
            //添加处方详情上提示信息的展示颜色类型
            //添加一次审核不通过，状态待审核
            Integer recipestatus = recipe.getStatus();
            if(RecipecCheckStatusConstant.First_Check_No_Pass == recipe.getCheckStatus()){
                recipestatus = RecipeStatusConstant.READY_CHECK_YS;
            }
            RecipeTipesColorTypeEnum colorType = RecipeTipesColorTypeEnum.fromRecipeStatus(recipestatus);
            if(null != colorType){
                map.put("tipsType", colorType.getShowType());
            }
            //患者处方单详情页按钮显示
            patientRecipeInfoBottonShow(map,recipe,order);
        }

        if (StringUtils.isEmpty(recipe.getMemo())) {
            recipe.setMemo("无");
        }

        //设置失效时间
        if (RecipeStatusConstant.CHECK_PASS == recipe.getStatus()) {
            recipe.setRecipeSurplusHours(getRecipeSurplusHours(recipe.getSignDate()));
        }

        //获取该医生所在科室，判断是否为儿科科室
        Integer departId = recipe.getDepart();
        DepartmentDTO departmentDTO = departmentService.get(departId);
        Boolean childRecipeFlag = false;
        if (!ObjectUtils.isEmpty(departmentDTO)) {
            if (departmentDTO.getName().contains("儿科") || departmentDTO.getName().contains("新生儿科")
                    || departmentDTO.getName().contains("儿内科") || departmentDTO.getName().contains("儿外科")) {
                childRecipeFlag = true;
            }
        }
        map.put("childRecipeFlag", childRecipeFlag);
        map.put("recipe", ObjectCopyUtils.convert(recipe, RecipeBean.class));

        //慢病列表配置
        try{
            IConfigurationCenterUtilsService configurationService = ApplicationUtils.getBaseService(IConfigurationCenterUtilsService.class);
            Integer recipeChooseChronicDisease = (Integer) configurationService.getConfiguration(recipe.getClinicOrgan(), "recipeChooseChronicDisease");
            map.put("recipeChooseChronicDisease", recipeChooseChronicDisease);
        }catch (Exception e){
            LOGGER.error("RecipeServiceSub.getRecipeAndDetailByIdImpl 获取慢病配置error, recipeId:{}", recipeId, e);
        }


        //设置订单信息
        if (StringUtils.isNotEmpty(recipe.getOrderCode())) {
            RecipeOrder recipeOrder = recipeOrderDAO.getByOrderCode(recipe.getOrderCode());
            map.put("recipeOrder", recipeOrder);
        }
        //设置医生手签图片id
        DoctorDTO doctorDTO = doctorService.getByDoctorId(recipe.getDoctor());
        if (doctorDTO != null){
            map.put("doctorSignImg",doctorDTO.getSignImage());
            map.put("doctorSignImgToken", FileAuth.instance().createToken(doctorDTO.getSignImage(), 3600L));
        }
        //设置药师手签图片id-----药师撤销审核结果不应该显示药师手签
        if (recipe.getChecker()!=null && recipe.getStatus() != RecipeStatusConstant.READY_CHECK_YS){
            DoctorDTO auditDTO = doctorService.getByDoctorId(recipe.getChecker());
            if (auditDTO != null){
                map.put("checkerSignImg",auditDTO.getSignImage());
                map.put("checkerSignImgToken", FileAuth.instance().createToken(auditDTO.getSignImage(), 3600L));
            }
        }
        //获取药师撤销原因
        if (recipe.getStatus() == RecipeStatusConstant.READY_CHECK_YS && ReviewTypeConstant.Preposition_Check.equals(recipe.getReviewType())){
            map.put("cancelReason", getCancelReasonForChecker(recipeId));
        }
        //Date:2019/12/16
        //Explain:添加判断展示处方参考价格
        //获取处方下的药品，判断是否有药品对应的医院药品金额为空，有的话不展示参考价格
        boolean flag = getShowReferencePriceFlag(recipe, recipedetails);
        map.put("showReferencePrice", flag);

        //Date:20200226
        //添加展示药师签名判断
        //1.不设置二次审核，审核通过展示；
        //2.设置二次审核，一次通过展示（没有审核不通过日志的且审核通过的）
        //总结的来说就是只要审核通过的并且没有不通过记录就展示
        boolean showChecker = isShowChecker(recipeId, recipe);
        if (recipe.getCheckMode() != null && recipe.getCheckMode() == 2) {
            //TODO HIS审方不显示药师签名
            showChecker = false;
        }
        map.put("showChecker", showChecker);

        //医生端/患者端获取处方扩展信息
        RecipeExtendDAO recipeExtendDAO = DAOFactory.getDAO(RecipeExtendDAO.class);
        RecipeExtend recipeExtend = recipeExtendDAO.getByRecipeId(recipeId);
        if (recipeExtend !=null){
            map.put("recipeExtend",recipeExtend);
        }

        //20200519 zhangx 是否展示退款按钮(重庆大学城退款流程)，前端调用patientRefundForRecipe
        map.put("showRefund",0);
        RecipeParameterDao recipeParameterDao = DAOFactory.getDAO(RecipeParameterDao.class);
        String patient_refund_organList = recipeParameterDao.getByName("patient_refund_organList");
        List<Integer> organIds=JSONUtils.parse(patient_refund_organList,ArrayList.class);
        if(organIds!=null && organIds.contains(recipe.getClinicOrgan())){
            map.put("showRefund",1);
        }


        return map;
    }

    private static void patientRecipeInfoBottonShow(Map<String, Object> map, Recipe recipe, RecipeOrder order) {
        //Date:20190904
        //Explain:添加患者点击按钮信息
        if(RecipeBussConstant.RECIPEMODE_NGARIHEALTH.equals(recipe.getRecipeMode()) ||
                RecipeBussConstant.RECIPEMODE_ZJJGPT.equals(recipe.getRecipeMode())){
            //获取配置项
            IConfigurationCenterUtilsService configService = BaseAPI.getService(IConfigurationCenterUtilsService.class);
            //添加按钮配置项key
            Object payModeDeploy = configService.getConfiguration(recipe.getClinicOrgan(), "payModeDeploy");
            if(null != payModeDeploy){
                List<String> configurations = new ArrayList<>(Arrays.asList((String[])payModeDeploy));
                //将配置的购药方式放在map上
                for (String configuration : configurations) {
                    map.put(configuration, 1);
                }
            }

            //互联网按钮信息（特殊化）
            if(RecipeBussConstant.RECIPEMODE_ZJJGPT.equals(recipe.getRecipeMode())){
                PayModeShowButtonBean payModeShowButtonBean = new PayModeShowButtonBean();
                if(map.get("supportOnline") != null && 1 == (Integer) map.get("supportOnline")){
                    payModeShowButtonBean.setSupportOnline(true);
                }
                if(map.get("supportToHos") != null && 1 == (Integer) map.get("supportToHos")){
                    payModeShowButtonBean.setSupportToHos(true);
                }

                //如果运营平台没设置按钮，那底下也不用走了
                if(payModeShowButtonBean.getSupportOnline() || payModeShowButtonBean.getSupportToHos()){
                    RecipeListService recipeListService = new RecipeListService();
                    recipeListService.initInternetModel(null, payModeShowButtonBean, recipe);

                    if(null != payModeShowButtonBean.getSupportToHos() && !payModeShowButtonBean.getSupportToHos()){
                        map.put("supportToHos", 0);
                    }
                    if(null != payModeShowButtonBean.getSupportOnline() && !payModeShowButtonBean.getSupportOnline()){
                        map.put("supportOnline", 0);
                    }
                }
            }
        }
        //Date:20190909
        //Explain:判断是否下载处方签

        //1.判断配置项中是否配置了下载处方签，
        //2.是否是后置的，后置的判断状态是否是已审核，已完成, 配送中，
        //3.如果不是后置的，判断实际金额是否为0：为0则ordercode关联则展示，不为0支付则展示
        boolean isDownload = getDownConfig(recipe, order);
        map.put("isDownload", isDownload);
        //date 20200508
        //设置展示配送到家的配送方式
        //判断当前处方对应的机构支持的配送药企包含的配送类型

        //首先判断按钮中配送药品购药方式是否展示，不展示购药方式按钮就不展示药企配送和医院配送
        boolean showSend = (null == map.get("supportOnline") ? false : 1 == Integer.parseInt(map.get("supportOnline").toString()));
        map.put("showSendToEnterprises", 0);
        map.put("showSendToHos", 0);
        //显示配送才判断具体显示哪个配送按钮
        if(showSend){
            DrugsEnterpriseDAO drugsEnterpriseDAO = DAOFactory.getDAO(DrugsEnterpriseDAO.class);
            List<Integer> payModeSupport = RecipeServiceSub.getDepSupportMode(RecipeBussConstant.PAYMODE_ONLINE);
            payModeSupport.addAll(RecipeServiceSub.getDepSupportMode(RecipeBussConstant.PAYMODE_COD));
            Long enterprisesSend = drugsEnterpriseDAO.getCountByOrganIdAndPayModeSupportAndSendType(recipe.getClinicOrgan(), payModeSupport, EnterpriseSendConstant.Enterprise_Send);
            Long hosSend = drugsEnterpriseDAO.getCountByOrganIdAndPayModeSupportAndSendType(recipe.getClinicOrgan(), payModeSupport, EnterpriseSendConstant.Hos_Send);
            if(null != enterprisesSend && 0 < enterprisesSend){
                map.put("showSendToEnterprises", 1);
            }
            if(null != hosSend && 0 < hosSend){
                map.put("showSendToHos", 1);
            }
            //不支持配送，则按钮都不显示--包括药店取药
            if (new Integer(2).equals(recipe.getDistributionFlag())){
                map.put("showSendToEnterprises", 0);
                map.put("showSendToHos", 0);
                map.put("supportTFDS",0);
                map.put("supportOnline",0);
            }
        }
        //date 2191011
        //添加处方详情上是否展示按钮
        boolean showButton = false;
        if(!((null == map.get("supportTFDS") || 0 == Integer.parseInt(map.get("supportTFDS").toString())) &&
                (null == map.get("supportOnline") || 0 == Integer.parseInt(map.get("supportOnline").toString())) &&
                (null == map.get("supportDownload") || 0 == Integer.parseInt(map.get("supportDownload").toString())) &&
                (null == map.get("supportToHos") || 0 == Integer.parseInt(map.get("supportToHos").toString())))){
            if(ReviewTypeConstant.Preposition_Check == recipe.getReviewType()){
                //带药师审核，审核一次不通过，待处理无订单
                if (RecipeStatusConstant.READY_CHECK_YS == recipe.getStatus()
                        || RecipecCheckStatusConstant.First_Check_No_Pass == recipe.getCheckStatus()
                        || (RecipeStatusConstant.CHECK_PASS == recipe.getStatus() && null == recipe.getOrderCode())) {

                    showButton = true;
                }
            }else{
                if (RecipeStatusConstant.CHECK_PASS == recipe.getStatus() && null == recipe.getOrderCode()) {

                    showButton = true;
                }
            }
        }

        map.put("showButton", showButton);
    }

    private static List<AuditMedicinesDTO> handleAnalysisByType(List<AuditMedicinesDTO> auditMedicines,String type){
        if(CollectionUtils.isNotEmpty(auditMedicines)){
            auditMedicines.forEach(auditMedicinesDTO -> {
                List<AuditMedicineIssueDTO> auditMedicineIssues = auditMedicinesDTO.getAuditMedicineIssues();
                List<AuditMedicineIssueDTO> resultAuditMedicineIssues = new ArrayList<>();
                auditMedicineIssues.forEach(auditMedicineIssueDTO->{
                    if(type.equals("medicines")){
                        if(null == auditMedicineIssueDTO.getDetailUrl()){
                            resultAuditMedicineIssues.add(auditMedicineIssueDTO);
                        }
                    }else if(type.equals("recipeDangers")){
                        if(null != auditMedicineIssueDTO.getDetailUrl()){
                            resultAuditMedicineIssues.add(auditMedicineIssueDTO);
                        }
                    }
                });
                auditMedicinesDTO.setAuditMedicineIssues(resultAuditMedicineIssues);
            });
        }
        return  auditMedicines;
    }

    public static String getCancelReasonForChecker(int recipeId) {
        RecipeLogDAO recipeLogDAO = DAOFactory.getDAO(RecipeLogDAO.class);
        List<RecipeLog> recipeLogs = recipeLogDAO.findByRecipeIdAndAfterStatusDesc(recipeId, RecipeStatusConstant.READY_CHECK_YS);
        String cancelReason ="";
        if (CollectionUtils.isNotEmpty(recipeLogs)) {
            if (new Integer(RecipeStatusConstant.CHECK_PASS).equals(recipeLogs.get(0).getBeforeStatus())){
                cancelReason = "药师已撤销审方结果,"+recipeLogs.get(0).getMemo()+"。请耐心等待药师再次审核";
            }
        }
        return cancelReason;
    }

    private static String getCancelReasonForPatient(int recipeId) {
        RecipeLogDAO recipeLogDAO = DAOFactory.getDAO(RecipeLogDAO.class);
        List<RecipeLog> recipeLogs = recipeLogDAO.findByRecipeIdAndAfterStatusDesc(recipeId, RecipeStatusConstant.REVOKE);
        String cancelReason ="";
        if (CollectionUtils.isNotEmpty(recipeLogs)) {
            cancelReason = "开方医生已撤销处方,撤销原因:"+recipeLogs.get(0).getMemo();
        }
        return cancelReason;
    }

    private static boolean isShowChecker(int recipeId, Recipe recipe) {
        boolean showChecker = false;
        RecipeCheckDAO recipeCheckDAO = DAOFactory.getDAO(RecipeCheckDAO.class);
        RecipeLogDAO recipeLogDAO = DAOFactory.getDAO(RecipeLogDAO.class);
        List<RecipeCheck> recipeCheckList = recipeCheckDAO.findByRecipeId(recipe.getRecipeId());
        if(CollectionUtils.isNotEmpty(recipeCheckList)){
            LOGGER.info("当前处方已有审核记录{}", recipeId);
            //取最新的审核记录
            RecipeCheck recipeCheck = recipeCheckList.get(0);
            //判断是否是通过的
            if(null != recipeCheck.getCheckStatus() && 1 == recipeCheck.getCheckStatus()){
                LOGGER.info("当前处方已有审核通过记录{}", recipeId);
                //判断有没有不通过的记录，没有就说明是直接审核通过的
                List<RecipeLog> recipeLogs = recipeLogDAO.findByRecipeIdAndAfterStatus(recipeId, RecipeStatusConstant.CHECK_NOT_PASS_YS);
                if(CollectionUtils.isEmpty(recipeLogs)){
                    LOGGER.info("当前处方已有审核通过中无审核不通过记录{}", recipeId);
                    showChecker = true;
                }
            }
        }
        return showChecker;
    }

    private static Object getBottomTextForPatient(Integer clinicOrgan) {
        try {
            IConfigurationCenterUtilsService configurationCenterUtilsService = ApplicationUtils.getBaseService(IConfigurationCenterUtilsService.class);
            return configurationCenterUtilsService.getConfiguration(clinicOrgan, "RecipeInfoBottomText");
        }catch (Exception e){
            LOGGER.error("getBottomTextForPatient error",e);
        }
        return null;
    }

    /**
     * @method  getShowReferencePriceFlag
     * @description 获取是否要展示参考价格
     * @date: 2019/12/17
     * @author: JRK
     * @param recipe 查询的处方
     * @param recipedetails 处方对应的药品详情
     * @return boolean 是否展示参考价格
     */
    private static boolean getShowReferencePriceFlag(Recipe recipe, List<Recipedetail> recipedetails) {
        OrganDrugListDAO drugDao = DAOFactory.getDAO(OrganDrugListDAO.class);
        boolean flag = true;
        OrganDrugList organDrug;
        if(null == recipedetails || 0 >= recipedetails.size()){
            flag = false;
        }else{
            //只要有一个药品的价格为空或0都不展示参考价格
            //date 2019/1/6
            //修改判断通过处方单详情中药品信息，如果价格有0则不显示价格
            for(Recipedetail recipedetail : recipedetails){
                if(null == recipedetail){
                    LOGGER.warn("当前机构{}下药品code{}的药品为空", recipe.getClinicOrgan(), recipedetail.getOrganDrugCode());
                    flag = false;
                    break;
                }
                if (null == recipedetail.getDrugCost() || 0 <= BigDecimal.ZERO.compareTo(recipedetail.getDrugCost())) {
                    LOGGER.info("当前机构药品{}的金额为空", recipedetail.getOrganDrugCode());
                    flag = false;
                    break;
                }
            }
        }
        return flag;
    }

    /**
     * @method  getDownConfig
     * @description 获取下载处方签的配置
     * @date: 2019/9/10
     * @author: JRK
     * @param recipe 当前处方
     * @param order 当前处方对应的订单
     * @return boolean 是否可以下载
     */
    private static boolean getDownConfig(Recipe recipe, RecipeOrder order) {
        //互联网的不需要下载处方笺
        if(RecipeBussConstant.RECIPEMODE_ZJJGPT.equals(recipe.getRecipeMode())){
            return false;
        }
        Boolean isDownload = false;
        //获取配置项
        IConfigurationCenterUtilsService configService = BaseAPI.getService(IConfigurationCenterUtilsService.class);
        //添加按钮配置项key
        Object downloadPrescription = configService.getConfiguration(recipe.getClinicOrgan(), "downloadPrescription");
        //date 2020/1/9
        //逻辑修改成：如果是下载处方购药方式的，无需判断配不配置【患者展示下载处方笺】
        //非下载处方的购药方式，只有配置了【患者展示下载处方笺】才判断是否展示下载按钮
        if(RecipeBussConstant.GIVEMODE_DOWNLOAD_RECIPE.equals(recipe.getGiveMode())){
            isDownload = canDown(recipe, order, showDownloadRecipeStatus, true);
        }else{
            if(null != downloadPrescription){
                boolean canDown = 0 != (Integer)downloadPrescription;
                if(canDown){
                    isDownload = canDown(recipe, order, showRecipeStatus, false);
                }else{
                    //没有配置则不会展示下载按钮
                    isDownload = false;
                }
            }
        }
        return isDownload;
    }

    /**
     * @method  canDown
     * @description 修改下载配置项
     * @date: 2019/9/10
     * @author: JRK
     * @param recipe 当前处方
     * @param order 当前处方的订单
       * @param isDownLoad 是否是下载处方
     * @return boolean 是否可以下载处方签
     */
    private static boolean canDown(Recipe recipe, RecipeOrder order, Integer[] status, Boolean isDownLoad) {
        boolean isDownload = false;
        //后置的时候判断处方的状态是一些状态的时候是展示按钮的
        if(ReviewTypeConstant.Postposition_Check == recipe.getReviewType()){
            if( Arrays.asList(status).contains(recipe.getStatus())) {
                isDownload = true;
            }
        }else if(ReviewTypeConstant.Not_Need_Check == recipe.getReviewType() && RecipeBussConstant.GIVEMODE_DOWNLOAD_RECIPE.equals(recipe.getGiveMode()) && RecipeStatusConstant.FINISH != recipe.getStatus()){
            //这里当是不需审核，且选择的下载处方的购药方式的时候，没有产生订单，直接判断没有选定购药方式
             if(1 == recipe.getChooseFlag()){
                 isDownload = true;
             }
        }else{
            //如果实际金额为0则判断有没有关联ordercode，实际金额不为0则判断是否已经支付,展示下载处方签，
            //当下载处方购药时，已完成处方不展示下载处方签
            if(null != recipe.getOrderCode() && null != order && !(isDownLoad && RecipeStatusConstant.FINISH == recipe.getStatus())){
                if(0 == order.getActualPrice() || (0 < order.getActualPrice() && 1 == recipe.getPayFlag()))
                    isDownload = true;

            }
        }
        return isDownload;
    }

    public static List<AuditMedicinesDTO> getAuditMedicineIssuesByRecipeId(int recipeId) {
        AuditMedicineIssueDAO issueDao = DAOFactory.getDAO(AuditMedicineIssueDAO.class);
        AuditMedicinesDAO medicinesDao = DAOFactory.getDAO(AuditMedicinesDAO.class);
        List<AuditMedicines> medicines = medicinesDao.findMedicinesByRecipeId(recipeId);
        List<AuditMedicinesDTO> list = Lists.newArrayList();
        if (medicines != null && medicines.size() > 0) {
            list = ObjectCopyUtils.convert(medicines, AuditMedicinesDTO.class);
            List<AuditMedicineIssue> issues = issueDao.findIssueByRecipeId(recipeId);
           /* Map<Integer,AuditMedicineIssue> maps = Maps.uniqueIndex(issues.iterator(),  new Function<AuditMedicineIssue, Integer>() {
                @Override
                public Integer apply(AuditMedicineIssue entity) {
                    return entity.getIssueId();
                }
            });*/
            if (issues != null && issues.size() > 0) {
                List<AuditMedicineIssue> issueList;
                for (AuditMedicinesDTO auditMedicinesDTO : list) {
                    issueList = Lists.newArrayList();
                    for (AuditMedicineIssue auditMedicineIssue : issues) {
                        if (null != auditMedicineIssue.getMedicineId() &&
                                auditMedicineIssue.getMedicineId().equals(auditMedicinesDTO.getId())) {
                            issueList.add(auditMedicineIssue);
                        }
                    }
                    auditMedicinesDTO.setAuditMedicineIssues(ObjectCopyUtils.convert(issueList, AuditMedicineIssueDTO.class));
                }
            }
        }
        return list;
    }

    /**
     * 状态文字提示（患者端）
     *
     * @param recipe
     * @return
     */
    public static String getTipsByStatusForPatient(Recipe recipe, RecipeOrder order) {
        Integer status = recipe.getStatus();
        Integer payMode = recipe.getPayMode();
        Integer payFlag = recipe.getPayFlag();
        Integer giveMode = recipe.getGiveMode();
        String orderCode = recipe.getOrderCode();
        String tips = "";
        switch (status) {
            case RecipeStatusConstant.FINISH:
                tips = "处方单已完结.";
                break;
            case RecipeStatusConstant.HAVE_PAY:
                if (RecipeBussConstant.GIVEMODE_SEND_TO_HOME.equals(giveMode)) {
                    //配送到家
                    tips = "您已支付，药品将尽快为您配送.";
                } else if (RecipeBussConstant.GIVEMODE_TO_HOS.equals(giveMode)) {
                    //医院取药
                    tips = "您已支付，请尽快到院取药.";
                }
                break;
            case RecipeStatusConstant.NO_OPERATOR:
            case RecipeStatusConstant.NO_PAY:
                tips = "由于您未及时缴费，该处方单已失效，请联系医生.";
                break;
            case RecipeStatusConstant.NO_DRUG:
                tips = "由于您未及时取药，该处方单已失效.";
                break;
            case RecipeStatusConstant.CHECK_PASS:
                if (null == payMode || null == giveMode) {
                    tips = "";
                } else if (RecipeBussConstant.PAYMODE_TO_HOS.equals(payMode) && 0 == payFlag) {
                    tips = "您已选择到院支付，请及时缴费并取药.";
                }

                if (StringUtils.isNotEmpty(orderCode) && null != order && 1 == order.getEffective()) {
                    tips = "您已选择配送到家，请及时支付并取药.";
                }

                break;
            case RecipeStatusConstant.READY_CHECK_YS:
                if (RecipeBussConstant.PAYMODE_ONLINE.equals(payMode)) {
                    //在线支付
                    tips = "您已支付，药品将尽快为您配送.";
                } else if (RecipeBussConstant.PAYMODE_COD.equals(payMode) || RecipeBussConstant.PAYMODE_TFDS.equals(payMode)) {
                    tips = "处方正在审核中.";
                }
                if (ReviewTypeConstant.Preposition_Check.equals(recipe.getReviewType())){
                    String reason = RecipeServiceSub.getCancelReasonForChecker(recipe.getRecipeId());
                    if (StringUtils.isNotEmpty(reason)){
                        tips = reason;
                    }
                }
                break;
            case RecipeStatusConstant.WAIT_SEND:
            case RecipeStatusConstant.CHECK_PASS_YS:
                if (RecipeBussConstant.PAYMODE_ONLINE.equals(payMode)) {
                    //在线支付
                    tips = "您已支付，药品将尽快为您配送.";
                } else if (RecipeBussConstant.PAYMODE_COD.equals(payMode)) {
                    //货到付款
                    tips = "药品将尽快为您配送.";
                } else if (RecipeBussConstant.PAYMODE_TFDS.equals(payMode)) {
                    tips = "请尽快前往药店取药.";
                }
                break;
            case RecipeStatusConstant.IN_SEND:
                if (RecipeBussConstant.PAYMODE_ONLINE.equals(payMode)) {
                    //在线支付
                    tips = "您已支付，药品正在配送中，请保持手机畅通.";
                } else if (RecipeBussConstant.PAYMODE_COD.equals(payMode)) {
                    //货到付款
                    tips = "药品正在配送中，请保持手机畅通.";
                }
                break;
            case RecipeStatusConstant.CHECK_NOT_PASS_YS:
                tips = "由于未通过审核，该处方单已失效，请联系医生.";
                if (StringUtils.isNotEmpty(orderCode) && null != order && 1 == order.getEffective()) {
                    if (RecipeBussConstant.PAYMODE_ONLINE.equals(payMode)) {
                        //在线支付
                        tips = "您已支付，药品将尽快为您配送.";
                    } else if (RecipeBussConstant.PAYMODE_COD.equals(payMode) || RecipeBussConstant.PAYMODE_TFDS.equals(payMode)) {
                        tips = "处方正在审核中.";
                    }
                }

                break;
            case RecipeStatusConstant.REVOKE:
                tips = "由于医生已撤销，该处方单已失效，请联系医生.";
                break;
            //天猫特殊状态
            case RecipeStatusConstant.USING:
                tips = "处理中";
                break;
            default:
                tips = "未知状态" + status;

        }
        return tips;
    }

    /**
     * 无法配送时间段文案提示
     * 处方单详情（待处理，待支付,药师未审核，状态为待配送,药师已审核，状态为待配送）
     */
    public static String getUnSendTitleForPatient(Recipe recipe) {
        String unSendTitle = "";
        switch (recipe.getStatus()) {
            case RecipeStatusConstant.CHECK_PASS:
            case RecipeStatusConstant.WAIT_SEND:
            case RecipeStatusConstant.CHECK_PASS_YS:
            case RecipeStatusConstant.READY_CHECK_YS:
                if (!RecipeBussConstant.PAYMODE_TFDS.equals(recipe.getPayMode())
                        && !RecipeBussConstant.PAYMODE_COD.equals(recipe.getPayMode())) {
                    unSendTitle = cacheService.getParam(ParameterConstant.KEY_RECIPE_UNSEND_TIP);
                }
                //患者选择药店取药但是未点击下一步而返回处方单详情，此时payMode会变成4，增加判断条件
                if (RecipeBussConstant.PAYMODE_TFDS.equals(recipe.getPayMode()) && 0 == recipe.getChooseFlag()) {
                    unSendTitle = cacheService.getParam(ParameterConstant.KEY_RECIPE_UNSEND_TIP);
                }
                break;
            default:
                unSendTitle = "";
        }
        return unSendTitle;
    }

    /**
     * 患者处方取药方式提示
     */
    public static String getRecipeGetModeTip(Recipe recipe) {
        String recipeGetModeTip = "";
        // 该处方不是只能配送处方，可以显示 到院取药 的文案
        if (1 != recipe.getChooseFlag() && !Integer.valueOf(1).equals(recipe.getDistributionFlag())) {
            String organName = StringUtils.isEmpty(recipe.getOrganName()) ? "医院" : recipe.getOrganName();
            // 邵逸夫特殊处理院区
            if (1 == recipe.getClinicOrgan()) {
                organName = "浙大附属邵逸夫医院庆春院区";
            }
            recipeGetModeTip = cacheService.getParam(ParameterConstant.KEY_RECIPE_GETMODE_TIP);
            recipeGetModeTip = LocalStringUtil.processTemplate(recipeGetModeTip, ImmutableMap.of("orgName", organName));
        }
        return recipeGetModeTip;

    }

    /**
     * 获取处方失效剩余时间
     *
     * @param signDate
     * @return
     */
    public static String getRecipeSurplusHours(Date signDate) {
        String recipeSurplusHours = "0.1";
        if (null != signDate) {
            long startTime = Calendar.getInstance().getTimeInMillis();
            long endTime = DateConversion.getDateAftXDays(signDate, 3).getTime();
            if (endTime > startTime) {
                DecimalFormat df = new DecimalFormat("0.00");
                recipeSurplusHours = df.format((endTime - startTime) / (float) (1000 * 60 * 60));
            }
        }

        return recipeSurplusHours;
    }

    /**
     * 配送模式选择
     *
     * @param payMode
     * @return
     */
    public static List<Integer> getDepSupportMode(Integer payMode) {
        //具体见DrugsEnterprise的payModeSupport字段
        //配送模式支持 0:不支持 1:线上付款 2:货到付款 3:药店取药 8:货到付款和药店取药 9:都支持
        List<Integer> supportMode = new ArrayList<>();
        if (null == payMode) {
            return supportMode;
        }

        if (RecipeBussConstant.PAYMODE_ONLINE.equals(payMode)) {
            supportMode.add(RecipeBussConstant.DEP_SUPPORT_ONLINE);
            supportMode.add(RecipeBussConstant.DEP_SUPPORT_ONLINE_TFDS);
        } else if (RecipeBussConstant.PAYMODE_COD.equals(payMode)) {
            supportMode.add(RecipeBussConstant.DEP_SUPPORT_COD);
            supportMode.add(RecipeBussConstant.DEP_SUPPORT_COD_TFDS);
        } else if (RecipeBussConstant.PAYMODE_TFDS.equals(payMode)) {
            supportMode.add(RecipeBussConstant.DEP_SUPPORT_TFDS);
            supportMode.add(RecipeBussConstant.DEP_SUPPORT_COD_TFDS);
            supportMode.add(RecipeBussConstant.DEP_SUPPORT_ONLINE_TFDS);
        } else if (RecipeBussConstant.PAYMODE_MEDICAL_INSURANCE.equals(payMode)) {
            //医保选用线上支付配送方式
            supportMode.add(RecipeBussConstant.DEP_SUPPORT_ONLINE);
        }

        if (CollectionUtils.isNotEmpty(supportMode)) {
            supportMode.add(RecipeBussConstant.DEP_SUPPORT_ALL);
        }

        return supportMode;
    }

    /**
     * 往咨询界面发送处方卡片
     *
     * @param recipe
     * @param details
     * @param rMap
     * @param send    true:发送卡片
     */
    public static void sendRecipeTagToPatient(Recipe recipe, List<Recipedetail> details,
                                              Map<String, Object> rMap, boolean send) {
        IConsultService iConsultService = ApplicationUtils.getConsultService(IConsultService.class);

        RecipeTagMsgBean recipeTagMsg = getRecipeMsgTag(recipe, details);
        //由于就诊人改造，已经可以知道申请人的信息，所以可以直接往当前咨询发消息
        if (StringUtils.isNotEmpty(recipe.getRequestMpiId()) && null != recipe.getDoctor()) {
            sendRecipeMsgTag(recipe.getRequestMpiId(), recipe, recipeTagMsg, rMap, send);
        } else if (StringUtils.isNotEmpty(recipe.getMpiid()) && null != recipe.getDoctor()) {
            //处方的患者编号在咨询单里其实是就诊人编号，不是申请人编号
            List<String> requestMpiIds = iConsultService.findPendingConsultByMpiIdAndDoctor(recipe.getMpiid(),
                    recipe.getDoctor());
            if (CollectionUtils.isNotEmpty(requestMpiIds)) {
                for (String requestMpiId : requestMpiIds) {
                    sendRecipeMsgTag(requestMpiId,recipe, recipeTagMsg, rMap, send);
                }
            }
        }
    }

    private static void sendRecipeMsgTag(String requestMpiId, Recipe recipe, RecipeTagMsgBean recipeTagMsg,
                                         Map<String, Object> rMap, boolean send) {
        INetworkclinicMsgService iNetworkclinicMsgService = AppContextHolder.getBean("nc.networkclinicMsgService", INetworkclinicMsgService.class);
        IConsultMessageService iConsultMessageService = ApplicationUtils.getConsultService(IConsultMessageService.class);
        //11月大版本改造--咨询id由前端传入
        /*//根据申请人mpiid，requestMode 获取当前咨询单consultId
        Integer consultId = null;
        List<Integer> consultIds = iConsultService.findApplyingConsultByRequestMpiAndDoctorId(requestMpiId,
                doctorId, RecipeSystemConstant.CONSULT_TYPE_RECIPE);
        if (CollectionUtils.isNotEmpty(consultIds)) {
            consultId = consultIds.get(0);
        }*/
        Integer consultId = recipe.getClinicId();
        Integer bussSource = recipe.getBussSource();
        if (consultId != null) {
            if (null != rMap && null == rMap.get("consultId")) {
                rMap.put("consultId", consultId);
                rMap.put("bussSource", bussSource);
            }

            if (send) {
                //11月大版本改造--咨询单或者网络门诊单是否正在处理中有他们那边判断
                LOGGER.info("sendRecipeMsgTag recipeTagMsg={}", JSONUtils.toString(recipeTagMsg));
                if (RecipeBussConstant.BUSS_SOURCE_WLZX.equals(bussSource)){
                    iNetworkclinicMsgService.handleRecipeMsg(consultId,recipeTagMsg,recipe.getDoctor());
                }else {
                    iConsultMessageService.handleRecipeMsg(consultId,recipeTagMsg,recipe.getDoctor());
                }
                /*ConsultBean consultBean = iConsultService.get(consultId);
                if (consultBean != null) {
                    //判断咨询单状态是否为处理中
                    if (consultBean.getConsultStatus() == RecipeSystemConstant.CONSULT_STATUS_HANDLING) {
                        if (StringUtils.isEmpty(consultBean.getSessionID())) {
                            recipeTagMsg.setSessionID(null);
                        } else {
                            recipeTagMsg.setSessionID(consultBean.getSessionID());
                        }
                        LOGGER.info("sendRecipeMsgTag recipeTagMsg={}", JSONUtils.toString(recipeTagMsg));
                        //将消息存入数据库consult_msg，并发送环信消息
                        iConsultMessageService.handleRecipeMsg(consultId, recipeTagMsg, consultBean.getConsultDoctor());
                    }
                }*/
            }
        }
    }

    /**
     * 获取处方卡片信息
     *
     * @param recipe
     * @param details
     * @return
     */
    private static RecipeTagMsgBean getRecipeMsgTag(Recipe recipe, List<Recipedetail> details) {
        DrugListDAO drugListDAO = DAOFactory.getDAO(DrugListDAO.class);

        //获取诊断疾病名称
        String diseaseName = recipe.getOrganDiseaseName();
        List<String> drugNames = Lists.newArrayList();
        if (RecipeUtil.isTcmType(recipe.getRecipeType())) {
            String useDose;
            for (Recipedetail r : details) {
                if (StringUtils.isNotEmpty(r.getUseDoseStr())){
                    useDose = r.getUseDoseStr();
                }else {
                    useDose = r.getUseDose()==null?r.getUseDoseStr():String.valueOf(r.getUseDose());
                }
                drugNames.add(r.getDrugName() + " * " +  useDose + r.getUseDoseUnit());
            }
        } else {
            //组装药品名称   药品名+商品名+规格
            List<Integer> drugIds = Lists.newArrayList();
            for (Recipedetail r : details) {
                drugIds.add(r.getDrugId());
            }
            List<DrugList> drugLists = drugListDAO.findByDrugIds(drugIds);
            OrganDrugListDAO organDrugListDAO = DAOFactory.getDAO(OrganDrugListDAO.class);
            for (Recipedetail recipedetail : details) {
                List<OrganDrugList> organDrugLists = organDrugListDAO.findByDrugIdAndOrganId(recipedetail.getDrugId(), recipe.getClinicOrgan());
                if (CollectionUtils.isNotEmpty(organDrugLists)) {
                    //判断非空
                    String drugName = StringUtils.isEmpty(organDrugLists.get(0).getDrugName()) ? "" : organDrugLists.get(0).getDrugName();
                    String saleName = StringUtils.isEmpty(organDrugLists.get(0).getSaleName()) ? "" : organDrugLists.get(0).getSaleName();
                    String drugSpec = StringUtils.isEmpty(organDrugLists.get(0).getDrugSpec()) ? "" : organDrugLists.get(0).getDrugSpec();
                    String drugForm = StringUtils.isEmpty(organDrugLists.get(0).getDrugForm()) ? "" : organDrugLists.get(0).getDrugForm();
                    String drugUnit = StringUtils.isEmpty(organDrugLists.get(0).getUnit()) ? "" : organDrugLists.get(0).getUnit();
                    //数据库中saleName字段可能包含与drugName相同的字符串,增加判断条件，将这些相同的名字过滤掉
                    StringBuilder drugAndSale = new StringBuilder("");
                    if (StringUtils.isNotEmpty(saleName)) {
                        String[] strArray = saleName.split("\\s+");
                        for (String saleName1 : strArray) {
                            if (!saleName1.equals(drugName)) {
                                drugAndSale.append(saleName1);
                            }
                        }
                    }
                    drugAndSale.append(drugName);

                    //拼装
                    drugNames.add(drugAndSale + drugForm + " "  + drugSpec + "/" + drugUnit);
                }
            }
        }

        RecipeTagMsgBean recipeTagMsg = new RecipeTagMsgBean();
        recipeTagMsg.setDiseaseName(diseaseName);
        recipeTagMsg.setDrugNames(drugNames);
        recipeTagMsg.setTitle(recipe.getPatientName() + "的电子处方单");
        if (null != recipe.getRecipeId()) {
            recipeTagMsg.setRecipeId(recipe.getRecipeId());
        }

        return recipeTagMsg;
    }

    /**
     * 处方撤销接口区分医生端和运营平台
     *
     * @param recipeId
     * @param flag
     * @return
     */
    public static Map<String, Object> cancelRecipeImpl(Integer recipeId, Integer flag, String name, String message) {
        LOGGER.info("cancelRecipe [recipeId：" + recipeId + "]");
        //获取订单
        RecipeOrderDAO orderDAO = DAOFactory.getDAO(RecipeOrderDAO.class);
        RecipeOrder order = orderDAO.getOrderByRecipeId(recipeId);
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        RecipeOrderService orderService = ApplicationUtils.getRecipeService(RecipeOrderService.class);
        RecipeHisService hisService = ApplicationUtils.getRecipeService(RecipeHisService.class);
        RecipeToHisMqService hisMqService = ApplicationUtils.getRecipeService(RecipeToHisMqService.class);

        //获取处方单
        Recipe recipe = recipeDAO.getByRecipeId(recipeId);
        Map<String, Object> rMap = Maps.newHashMap();
        Boolean result = false;
        //医生撤销提醒msg，供医生app端使用
        String msg = "";
        if (null == recipe) {
            msg = "该处方单不存在";
            rMap.put("result", result);
            rMap.put("msg", msg);
            return rMap;
        }

        String recipeMode = recipe.getRecipeMode();
        //获取撤销前处方单状态
        Integer beforeStatus = recipe.getStatus();
        //不能撤销的情况:1 患者已支付 2 药师已审核(不管是否通过)
        if (Integer.valueOf(RecipeStatusConstant.REVOKE).equals(recipe.getStatus())) {
            msg = "该处方单已撤销，不能进行撤销操作";
        }
        if (recipe.getStatus() == RecipeStatusConstant.UNSIGN) {
            msg = "暂存的处方单不能进行撤销";
        }
        if (Integer.valueOf(1).equals(recipe.getPayFlag())) {
            msg = "该处方单用户已支付，不能进行撤销操作";
        }
        if (recipe.getStatus() == RecipeStatusConstant.HIS_FAIL
                || recipe.getStatus() == RecipeStatusConstant.NO_DRUG
                || recipe.getStatus() == RecipeStatusConstant.NO_PAY
                || recipe.getStatus() == RecipeStatusConstant.NO_OPERATOR) {
            msg = "该处方单已取消，不能进行撤销操作";
        }
        if (Integer.valueOf(1).equals(recipe.getChooseFlag())) {
            msg = "患者已选择购药方式，不能进行撤销操作";
        }
        if (1 == flag) {
            if (StringUtils.isEmpty(name)) {
                msg = "姓名不能为空";
            }
            if (StringUtils.isEmpty(message)) {
                msg = "撤销原因不能为空";
            }
        }
        //处方撤销信息，供记录日志使用
        StringBuilder memo = new StringBuilder(msg);
        if (StringUtils.isEmpty(msg)) {
            Map<String, Integer> changeAttr = Maps.newHashMap();
            if(order !=null){
                if (!recipe.canMedicalPay()) {
                    changeAttr.put("chooseFlag", 1);
                }
                orderService.cancelOrder(order, OrderStatusConstant.CANCEL_AUTO);
            }
            result = recipeDAO.updateRecipeInfoByRecipeId(recipeId, RecipeStatusConstant.REVOKE, changeAttr);

            if (result) {
                msg = "处方撤销成功";
                //向患者推送处方撤销消息
                if (!(RecipeStatusConstant.READY_CHECK_YS == recipe.getStatus() && recipe.canMedicalPay())) {
                    //医保的处方待审核时患者无法看到处方，不发送撤销消息提示
                    RecipeMsgService.batchSendMsg(recipe, RecipeStatusConstant.REVOKE);
                }
                memo.append(msg);
                //HIS消息发送
                if(RecipeBussConstant.RECIPEMODE_NGARIHEALTH.equals(recipeMode)) {

                    boolean succFlag = hisService.recipeStatusUpdate(recipeId);
                    if (succFlag) {
                        memo.append(",HIS推送成功");
                    } else {
                        memo.append(",HIS推送失败");
                    }
                } else if (RecipeBussConstant.RECIPEMODE_ZJJGPT.equals(recipeMode)){
                    hisMqService.recipeStatusToHis(HisMqRequestInit.initRecipeStatusToHisReq(recipe,
                            HisBussConstant.TOHIS_RECIPE_STATUS_REVOKE));
                    memo.append(",HIS推送成功");
                    OrganAndDrugsepRelationDAO organAndDrugsepRelationDAO = DAOFactory.getDAO(OrganAndDrugsepRelationDAO.class);
                    List<DrugsEnterprise> drugsEnterprises = organAndDrugsepRelationDAO.findDrugsEnterpriseByOrganIdAndStatus(recipe.getClinicOrgan(), 1);
                    for (DrugsEnterprise drugsEnterprise : drugsEnterprises) {
                        if ("aldyf".equals(drugsEnterprise.getCallSys())) {
                            //向阿里大药房推送处方撤销通知
                            DrugEnterpriseResult drugEnterpriseResult = null;
                            try {
                                AldyfRemoteService aldyfRemoteService = ApplicationUtils.getRecipeService(AldyfRemoteService.class);
                                drugEnterpriseResult = aldyfRemoteService.updatePrescriptionStatus(recipe.getRecipeCode(), AlDyfRecipeStatusConstant.RETREAT);
                            } catch (Exception e) {
                                LOGGER.warn("cancelRecipeImpl  向阿里大药房推送处方撤销通知,{}",JSONUtils.toString(drugEnterpriseResult), e);
                            }
                            LOGGER.info("向阿里大药房推送处方撤销通知,{}",JSONUtils.toString(drugEnterpriseResult));
                        }
                    }
                }
                LOGGER.info("cancelRecipe result:" + memo);
                //如果是待审核要取消未结束任务
                if (RecipeStatusConstant.READY_CHECK_YS == beforeStatus) {
                    ApplicationUtils.getBaseService(IAsynDoBussService.class).fireEvent(new BussCancelEvent(recipeId, BussTypeConstant.RECIPE));
                }
                //处方撤销后将状态设为已撤销，供记录日志使用
                recipe.setStatus(RecipeStatusConstant.REVOKE);
                //推送处方到监管平台
                RecipeBusiThreadPool.submit(new PushRecipeToRegulationCallable(recipe.getRecipeId(),1));
            } else {
                msg = "未知原因，处方撤销失败";
                memo.append("," + msg);
            }
        }else {
            rMap.put("result", result);
            rMap.put("msg", msg);
            return rMap;
        }

        if (1 == flag) {
            memo.append("处方撤销成功。" + "撤销人：" + name + ",撤销原因：" + message);
        }else {
            if (result){
                if (StringUtils.isNotEmpty(message)){
                    memo = new StringBuilder(message);
                }else {
                    memo = new StringBuilder("无");
                }

            }
        }
        //记录日志
        RecipeLogService.saveRecipeLog(recipeId, beforeStatus, recipe.getStatus(), memo.toString());
        rMap.put("result", result);
        rMap.put("msg", msg);
        LOGGER.info("cancelRecipe execute ok! rMap:" + JSONUtils.toString(rMap));
        return rMap;
    }

    /**
     * 获取医院时候可以药店取药
     */
    public static Boolean getDrugToHos(Integer recipeId, Integer organId) {
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        Recipe recipe = recipeDAO.get(recipeId);
        if(null == recipe){
            LOGGER.info("处方不存在 recipeId[{}]", recipeId);
            return false;
        }
        //平台的取平台配置项
        if(RecipeBussConstant.RECIPEMODE_NGARIHEALTH.equals(recipe.getRecipeMode())){
            //获取配置项
            IConfigurationCenterUtilsService configService = BaseAPI.getService(IConfigurationCenterUtilsService.class);
            Object payModeDeploy = configService.getConfiguration(organId, "payModeDeploy");
            if(null == payModeDeploy){
                return false;
            }
            List<String> configurations = new ArrayList<>(Arrays.asList((String[])payModeDeploy));
            //将购药方式的显示map对象转化为页面展示的对象
            Map<String, Boolean> buttonMap = new HashMap<>(10);
            for (String configuration : configurations) {
                buttonMap.put(configuration, true);
            }
            //通过配置获取是否可以到院取药
            return (null == buttonMap.get("supportToHos") || !buttonMap.get("supportToHos")) ? false : true;
        }else{
            return organService.getTakeMedicineFlagById(organId);
        }


    }

    /**
     * 杭州市互联网获取是否是医保病人
     * @param mpiid
     * @param clinicOrgan
     * @return
     */
    public static Boolean isMedicalPatient(String mpiid, Integer clinicOrgan) {
        HealthCardService healthCardService = ApplicationUtils.getBasicService(HealthCardService.class);
        OrganService organService = ApplicationUtils.getBasicService(OrganService.class);
        //杭州市互联网医院监管中心 管理单元eh3301
        OrganDTO organDTO = organService.getByManageUnit("eh3301");
        if (organDTO!=null){
            //医保卡id ----
            String medicareCardId = healthCardService.getMedicareCardId(mpiid, organDTO.getOrganId());
            if (StringUtils.isNotEmpty(medicareCardId)){
                return true;
            }
        }

        return false;
    }

    /**
     * 是否配置了杭州市互联网
     * @param clinicOrgan
     * @return
     */
    public static boolean isNotHZInternet(Integer clinicOrgan) {
        OrganAndDrugsepRelationDAO dao = DAOFactory.getDAO(OrganAndDrugsepRelationDAO.class);
        List<DrugsEnterprise> enterprises = dao.findDrugsEnterpriseByOrganIdAndStatus(clinicOrgan, 1);
        if (CollectionUtils.isNotEmpty(enterprises)){
            if ("hzInternet".equals(enterprises.get(0).getCallSys())){
                return false;
            }
        }
        return true;
    }

    /**
     * 是否是配置了江苏监管平台的机构
     * @param clinicOrgan
     * @return
     */
    public static boolean isJSOrgan(Integer clinicOrgan) {
        try {
            IHisServiceConfigService configService = AppContextHolder.getBean("his.hisServiceConfig", IHisServiceConfigService.class);
            List<ServiceConfigResponseTO> serviceConfigResponseTOS = configService.findAllRegulationOrgan();
            if (CollectionUtils.isEmpty(serviceConfigResponseTOS)) {
                return false;
            }
            //判断机构是否关联了江苏监管平台
            List<Integer> organList = serviceConfigResponseTOS.stream()
                    .filter(regulation-> regulation.getRegulationAppDomainId().startsWith("jssjgpt"))
                    .map(ServiceConfigResponseTO::getOrganid).collect(Collectors.toList());
            LOGGER.info("isJSOrgan organId={}",JSONUtils.toString(organList));
            if (organList.contains(clinicOrgan)) {
                return true;
            }
        }catch (Exception e){
            LOGGER.error("isJSOrgan error",e);
            return false;
        }
        return false;
    }

    /**
     * 获取机构是否支持二次审方
     * @param clinicOrgan
     * @return
     */
    public static boolean canSecondAudit(Integer clinicOrgan) {
        //默认不支持
        Boolean flag =false;
        try {
            IConfigurationCenterUtilsService configService = BaseAPI.getService(IConfigurationCenterUtilsService.class);
            flag = (Boolean) configService.getConfiguration(clinicOrgan, "doctorSecondAuditFlag");
        } catch (Exception e) {
            LOGGER.error("canSecondAudit 获取机构配置异常",e);
        }
        return flag;
    }

    /**
     * 设置没有用药天数字符的处方详情
     *
     * @param recipeDetails
     * @return
     */
    public static void setUseDaysBToDetali(List<Recipedetail> recipeDetails) {
       for(Recipedetail recipedetail : recipeDetails){
           if(StringUtils.isEmpty(recipedetail.getUseDaysB())){

               recipedetail.setUseDaysB(null != recipedetail.getUseDays() ? recipedetail.getUseDays().toString() : "0");

           }
       }
    }
}
