package recipe.service.manager;

import com.alibaba.fastjson.JSON;
import com.ngari.patient.dto.DepartmentDTO;
import com.ngari.patient.service.DepartmentService;
import com.ngari.recipe.entity.Recipe;
import com.ngari.recipe.entity.RecipeExtend;
import com.ngari.recipe.recipe.model.RecipeBean;
import ctd.persistence.DAOFactory;
import ctd.util.AppContextHolder;
import ctd.util.BeanUtils;
import ctd.util.JSONUtils;
import eh.cdr.api.service.IDocIndexService;
import eh.cdr.api.vo.DocIndexBean;
import eh.cdr.api.vo.DocIndexExtBean;
import eh.cdr.api.vo.MedicalDetailBean;
import eh.cdr.api.vo.MedicalInfoBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import recipe.bean.EmrDetailDTO;
import recipe.bean.EmrDetailValueDTO;
import recipe.comment.RecipeEmrComment;
import recipe.dao.RecipeExtendDAO;
import recipe.util.ByteUtils;

import javax.annotation.Resource;
import java.util.*;

/**
 * @author yinsheng
 * @date 2020\8\18 0018 08:57
 */
@Service
public class EmrRecipeManager {
    private static final Logger logger = LoggerFactory.getLogger(EmrRecipeManager.class);
    /**
     * 病历状态 2 暂存 4 已使用
     */
    private static Integer DOC_STATUS_HOLD = 2;
    private static Integer DOC_STATUS_USE = 4;

    @Resource
    private IDocIndexService docIndexService;
    @Resource
    private DepartmentService departmentService;

    /**
     * 保存电子病历 主要用于兼容老数据结构
     *
     * @param recipe
     * @param recipeExt
     */
    public void saveMedicalInfo(RecipeBean recipe, RecipeExtend recipeExt) {
        logger.info("EmrRecipeManager saveMedicalInfo recipe:{},recipeExt:{}", JSONUtils.toString(recipe), JSONUtils.toString(recipeExt));
        if (null != recipeExt.getDocIndexId()) {
            return;
        }
        if (null != recipe.getEmrStatus() && recipe.getEmrStatus()) {
            return;
        }
        try {
            addMedicalInfo(recipe, recipeExt, DOC_STATUS_HOLD);
            logger.info("EmrRecipeManager saveMedicalInfo end recipeExt={}", recipeExt.getDocIndexId());
        } catch (Exception e) {
            logger.error("EmrRecipeManager saveMedicalInfo 电子病历保存失败", e);
        }
    }

    /**
     * 批量处理老数据接口 只用发布时处理一次
     *
     * @param recipe
     * @param recipeExt
     */
    public void saveDocList(RecipeBean recipe, RecipeExtend recipeExt) {
        logger.info("EmrRecipeManager saveDocList recipe:{},recipeExt:{}", JSONUtils.toString(recipe), JSONUtils.toString(recipeExt));
        try {
            addMedicalInfo(recipe, recipeExt, DOC_STATUS_USE);
        } catch (Exception e) {
            logger.error("EmrRecipeManager saveDocList 电子病历保存失败", e);
        }
        logger.info("EmrRecipeManager updateMedicalInfo end recipeExt={}", recipeExt.getDocIndexId());
    }

    /**
     * 更新电子病例 用于相同处方多次暂存或者修改时 兼容新老版本
     *
     * @param recipe
     * @param recipeExt
     */
    public void updateMedicalInfo(RecipeBean recipe, RecipeExtend recipeExt) {
        logger.info("EmrRecipeManager updateMedicalInfo recipe:{},recipeExt:{}", JSONUtils.toString(recipe), JSONUtils.toString(recipeExt));
        if (null == recipeExt.getDocIndexId()) {
            try {
                addMedicalInfo(recipe, recipeExt, DOC_STATUS_HOLD);
            } catch (Exception e) {
                logger.error("EmrRecipeManager updateMedicalInfo 电子病历保存失败", e);
            }
            return;
        }
        if (null != recipe.getEmrStatus() && recipe.getEmrStatus()) {
            return;
        }
        try {
            //更新电子病历
            RecipeExtendDAO recipeExtendDAO = DAOFactory.getDAO(RecipeExtendDAO.class);
            RecipeExtend recipeExtend = recipeExtendDAO.getByRecipeId(recipeExt.getRecipeId());
            MedicalDetailBean medicalDetailBean = new MedicalDetailBean();
            medicalDetailBean.setDocIndexId(recipeExtend.getDocIndexId());
            setMedicalDetailBean(recipe, recipeExt, medicalDetailBean);
            logger.info("EmrRecipeManager updateMedicalInfo medicalDetailBean :{}", JSONUtils.toString(medicalDetailBean));
            docIndexService.updateMedicalDetail(medicalDetailBean);
        } catch (Exception e) {
            logger.error("EmrRecipeManager updateMedicalInfo 电子病历更新失败", e);
        }
        logger.info("EmrRecipeManager updateMedicalInfo end recipeExt={}", recipeExt.getDocIndexId());
    }

    /**
     * 更新电子病例为已经使用状态
     *
     * @param docId 电子病例id
     */
    public void updateDocStatus(Integer docId) {
        logger.info("EmrRecipeManager updateDocStatus docId={}", docId);
        if (null == docId) {
            return;
        }

        Boolean result = docIndexService.updateStatusByDocIndexId(docId, DOC_STATUS_USE);
        logger.info("EmrRecipeManager updateDocStatus docId={} boo={}", docId, result);
    }

    /**
     * 查询电子病例，主要用于兼容老数据结构
     *
     * @param recipeBean
     * @param recipeExtend
     */
    public static void getMedicalInfo(RecipeBean recipeBean, RecipeExtend recipeExtend) {
        Recipe recipe = new Recipe();
        BeanUtils.copy(recipeBean, recipe);
        getMedicalInfo(recipe, recipeExtend);
        recipeBean.setOrganDiseaseName(recipe.getOrganDiseaseName());
        recipeBean.setOrganDiseaseId(recipe.getOrganDiseaseId());
    }

    /**
     * 查询电子病例，主要用于兼容老数据结构
     *
     * @param recipe
     * @param recipeExtend
     */
    public static void getMedicalInfo(Recipe recipe, RecipeExtend recipeExtend) {
        if (null == recipeExtend || null == recipeExtend.getDocIndexId()) {
            logger.info("EmrRecipeManager getMedicalInfo recipeExtend={}", JSONUtils.toString(recipeExtend));
            return;
        }
        IDocIndexService docIndexService = AppContextHolder.getBean("ecdr.docIndexService", IDocIndexService.class);
        Map<String, Object> medicalInfoMap;
        try {
            medicalInfoMap = docIndexService.getMedicalInfoByDocIndexId(recipeExtend.getDocIndexId());
        } catch (Exception e) {
            logger.error("EmrRecipeManager getMedicalInfo getMedicalInfoByDocIndexId error = {}", e.getMessage());
            return;
        }
        logger.info("EmrRecipeManager getMedicalInfo medicalInfoMap={}", JSON.toJSONString(medicalInfoMap));

        if (CollectionUtils.isEmpty(medicalInfoMap)) {
            return;
        }
        Object medicalDetail = medicalInfoMap.get("medicalDetailBean");
        if (ObjectUtils.isEmpty(medicalDetail)) {
            return;
        }
        MedicalDetailBean medicalDetailBean = JSON.parseObject(JSON.toJSONString(medicalDetail), MedicalDetailBean.class);

        if (!recipeExtend.getDocIndexId().equals(medicalDetailBean.getDocIndexId())) {
            return;
        }
        List<EmrDetailDTO> detail = JSON.parseArray(medicalDetailBean.getDetail(), EmrDetailDTO.class);
        if (CollectionUtils.isEmpty(detail)) {
            return;
        }
        for (EmrDetailDTO detailDTO : detail) {
            String value = detailDTO.getValue();
            if (StringUtils.isEmpty(value)) {
                continue;
            }
            String type = detailDTO.getType();
            if (!RecipeEmrComment.TEXT_AREA.equals(type) && !RecipeEmrComment.MULTI_SEARCH.equals(type)) {
                logger.warn("EmrRecipeManager getMedicalInfo detail={}", JSONUtils.toString(detail));
                continue;
            }
            String key = detailDTO.getKey();
            if (RecipeEmrComment.COMPLAIN.equals(key) && StringUtils.isEmpty(recipeExtend.getMainDieaseDescribe())) {
                recipeExtend.setMainDieaseDescribe(value);
                continue;
            }
            if (RecipeEmrComment.CURRENT_MEDICAL_HISTORY.equals(key) && StringUtils.isEmpty(recipeExtend.getCurrentMedical())) {
                recipeExtend.setCurrentMedical(value);
                continue;
            }
            if (RecipeEmrComment.PAST_MEDICAL_HISTORY.equals(key) && StringUtils.isEmpty(recipeExtend.getHistroyMedical())) {
                recipeExtend.setHistroyMedical(value);
                continue;
            }
            if (RecipeEmrComment.MEDICAL_HISTORY.equals(key) && StringUtils.isEmpty(recipeExtend.getHistoryOfPresentIllness())) {
                recipeExtend.setHistoryOfPresentIllness(value);
                continue;
            }
            if (RecipeEmrComment.ALLERGY_HISTORY.equals(key) && StringUtils.isEmpty(recipeExtend.getAllergyMedical())) {
                recipeExtend.setAllergyMedical(value);
                continue;
            }
            if (RecipeEmrComment.PHYSICAL_EXAMINATION.equals(key) && StringUtils.isEmpty(recipeExtend.getPhysicalCheck())) {
                recipeExtend.setPhysicalCheck(value);
                continue;
            }
            if (RecipeEmrComment.PROCESSING_METHOD.equals(key) && StringUtils.isEmpty(recipeExtend.getHandleMethod())) {
                recipeExtend.setHandleMethod(value);
                continue;
            }
            if (RecipeEmrComment.MEMO.equals(key) && StringUtils.isEmpty(recipe.getMemo())) {
                recipe.setMemo(value);
                continue;
            }
            /**诊断 ，中医症候特殊处理*/
            getMultiSearch(detailDTO, recipe, recipeExtend);
            logger.info("EmrRecipeManager getMedicalInfo recipe={}，recipeExtend={}", JSONUtils.toString(recipe), JSONUtils.toString(recipeExtend));
        }
    }


    /**
     * 新增电子病历 主要用于兼容老数据结构
     *
     * @param recipeExt
     */
    private void addMedicalInfo(RecipeBean recipe, RecipeExtend recipeExt, Integer docStatus) {
        if (null == recipeExt) {
            return;
        }
        //保存电子病历
        MedicalInfoBean medicalInfoBean = new MedicalInfoBean();
        //设置病历索引信息
        DocIndexBean docIndexBean = new DocIndexBean();
        docIndexBean.setClinicId(recipe.getClinicId());
        docIndexBean.setMpiid(recipe.getMpiid());
        docIndexBean.setDocClass(11);
        docIndexBean.setDocType("0");
        docIndexBean.setDocTitle("电子处方病历");
        docIndexBean.setDocSummary("电子处方病历");
        docIndexBean.setCreateOrgan(recipe.getClinicOrgan());
        docIndexBean.setCreateDepart(recipe.getDepart());
        try {
            DepartmentDTO department = departmentService.get(recipe.getDepart());
            if (department != null) {
                docIndexBean.setDepartName(department.getName());
            }
        } catch (Exception e) {
            logger.error("EmrRecipeManager departmentService error", e);
        }
        docIndexBean.setCreateDoctor(recipe.getDoctor());
        docIndexBean.setDoctorName(recipe.getDoctorName());
        docIndexBean.setCreateDate(recipe.getCreateDate());
        docIndexBean.setGetDate(new Date());
        docIndexBean.setDoctypeName("电子处方病历");
        docIndexBean.setDocStatus(docStatus);
        docIndexBean.setDocFlag(0);
        docIndexBean.setOrganNameByUser(recipe.getOrganName());
        docIndexBean.setClinicPersonName(recipe.getPatientName());
        docIndexBean.setLastModify(new Date());
        medicalInfoBean.setDocIndexBean(docIndexBean);
        //设置病历索引扩展信息
        List<DocIndexExtBean> docIndexExtBeanList = new ArrayList<>();
        DocIndexExtBean docIndexExtBean = new DocIndexExtBean();
        //业务类型 1 处方 2 复诊 3 检查 4 检验
        docIndexExtBean.setBussType(1);
        docIndexExtBean.setBussId(recipeExt.getRecipeId());
        docIndexExtBeanList.add(docIndexExtBean);
        medicalInfoBean.setDocIndexExtBeanList(docIndexExtBeanList);
        //设置病历详情
        MedicalDetailBean medicalDetailBean = new MedicalDetailBean();
        setMedicalDetailBean(recipe, recipeExt, medicalDetailBean);
        medicalInfoBean.setMedicalDetailBean(medicalDetailBean);
        logger.info("EmrRecipeManager addMedicalInfo  medicalDetailBean:{}", JSONUtils.toString(medicalInfoBean));
        Integer docId = docIndexService.saveMedicalInfo(medicalInfoBean);
        recipeExt.setDocIndexId(docId);
        logger.info("EmrRecipeManager addMedicalInfo end docId={}", docId);
    }

    /**
     * 组织电子病历明细数据 用于调用保存接口 主要为了兼容老版本
     *
     * @param recipe
     * @param recipeExt
     * @param medicalDetailBean
     */
    private void setMedicalDetailBean(RecipeBean recipe, RecipeExtend recipeExt, MedicalDetailBean medicalDetailBean) {
        List<EmrDetailDTO> detail = new ArrayList<>();
        //设置主诉
        detail.add(new EmrDetailDTO(RecipeEmrComment.COMPLAIN, "主诉", RecipeEmrComment.TEXT_AREA, ByteUtils.isEmpty(recipeExt.getMainDieaseDescribe()), true));
        //病史
        detail.add(new EmrDetailDTO(RecipeEmrComment.MEDICAL_HISTORY, "病史", RecipeEmrComment.TEXT_AREA, ByteUtils.isEmpty(recipeExt.getHistoryOfPresentIllness()), true));
        //设置现病史
        detail.add(new EmrDetailDTO(RecipeEmrComment.CURRENT_MEDICAL_HISTORY, "现病史", RecipeEmrComment.TEXT_AREA, ByteUtils.isEmpty(recipeExt.getCurrentMedical()), false));
        //设置既往史
        detail.add(new EmrDetailDTO(RecipeEmrComment.PAST_MEDICAL_HISTORY, "既往史", RecipeEmrComment.TEXT_AREA, ByteUtils.isEmpty(recipeExt.getHistroyMedical()), false));
        //设置过敏史
        detail.add(new EmrDetailDTO(RecipeEmrComment.ALLERGY_HISTORY, "过敏史", RecipeEmrComment.TEXT_AREA, ByteUtils.isEmpty(recipeExt.getAllergyMedical()), false));
        //设置体格检查
        detail.add(new EmrDetailDTO(RecipeEmrComment.PHYSICAL_EXAMINATION, "体格检查", RecipeEmrComment.TEXT_AREA, ByteUtils.isEmpty(recipeExt.getPhysicalCheck()), false));
        //设置处理方法
        detail.add(new EmrDetailDTO(RecipeEmrComment.PROCESSING_METHOD, "处理方法", RecipeEmrComment.TEXT_AREA, ByteUtils.isEmpty(recipeExt.getHandleMethod()), false));
        //设置注意事项
        detail.add(new EmrDetailDTO(RecipeEmrComment.MEMO, "注意事项", RecipeEmrComment.TEXT_AREA, ByteUtils.isEmpty(recipe.getMemo()), false));
        //设置诊断
        if (!StringUtils.isEmpty(recipe.getOrganDiseaseName())) {
            String[] diseaseNames = ByteUtils.split(recipe.getOrganDiseaseName(), ByteUtils.SEMI_COLON_CH);
            String[] diseaseIds = ByteUtils.split(recipe.getOrganDiseaseId(), ByteUtils.SEMI_COLON_CH);
            detail.add(new EmrDetailDTO(RecipeEmrComment.DIAGNOSIS, "诊断", RecipeEmrComment.MULTI_SEARCH, getEmrDetailValueDTO(diseaseNames, diseaseIds), true));
        }
        //设置中医证候
        if (!StringUtils.isEmpty(recipeExt.getSymptomName())) {
            String[] symptomNames = ByteUtils.split(recipeExt.getSymptomName(), ByteUtils.SEMI_COLON_EN);
            String[] symptomIds = ByteUtils.split(recipeExt.getSymptomId(), ByteUtils.SEMI_COLON_EN);
            detail.add(new EmrDetailDTO(RecipeEmrComment.TCM_SYNDROME, "中医证候", RecipeEmrComment.MULTI_SEARCH, getEmrDetailValueDTO(symptomNames, symptomIds), false));
        }

        medicalDetailBean.setDetail(JSONUtils.toString(detail));
    }

    /**
     * 查询时组织特殊字段
     *
     * @param detail
     * @param recipe
     * @param recipeExtend
     */
    private static void getMultiSearch(EmrDetailDTO detail, Recipe recipe, RecipeExtend recipeExtend) {
        /**诊断 ，中医症候特殊处理*/
        if (!RecipeEmrComment.MULTI_SEARCH.equals(detail.getType())) {
            logger.warn("EmrRecipeManager getMultiSearch detail={}", JSONUtils.toString(detail));
            return;
        }
        List<EmrDetailValueDTO> values = JSON.parseArray(detail.getValue(), EmrDetailValueDTO.class);
        StringBuilder names = new StringBuilder();
        StringBuilder ids = new StringBuilder();
        if (RecipeEmrComment.DIAGNOSIS.equals(detail.getKey())) {
            values.forEach(b -> {
                names.append(b.getName()).append(ByteUtils.SEMI_COLON_CH);
                ids.append(b.getCode()).append(ByteUtils.SEMI_COLON_CH);
            });
            if (StringUtils.isEmpty(recipe.getOrganDiseaseName()) && !StringUtils.isEmpty(names)) {
                recipe.setOrganDiseaseName(ByteUtils.subString(names));
            }
            if (StringUtils.isEmpty(recipe.getOrganDiseaseId()) && !StringUtils.isEmpty(ids)) {
                recipe.setOrganDiseaseId(ByteUtils.subString(ids));
            }
        } else if (RecipeEmrComment.TCM_SYNDROME.equals(detail.getKey())) {
            values.forEach(b -> {
                names.append(b.getName()).append(ByteUtils.SEMI_COLON_EN);
                ids.append(b.getCode()).append(ByteUtils.SEMI_COLON_EN);
            });
            if (StringUtils.isEmpty(recipeExtend.getSymptomName()) && !StringUtils.isEmpty(names)) {
                recipeExtend.setSymptomName(ByteUtils.subString(names));
            }
            if (StringUtils.isEmpty(recipeExtend.getSymptomId()) && !StringUtils.isEmpty(ids)) {
                recipeExtend.setSymptomId(ByteUtils.subString(ids));
            }
        } else {
            logger.warn("EmrRecipeManager getMultiSearch detail={}", JSONUtils.toString(detail));
        }
    }

    /**
     * 组织特殊value字段
     *
     * @param names
     * @param ids
     * @return
     */
    private String getEmrDetailValueDTO(String[] names, String[] ids) {

        List<EmrDetailValueDTO> diagnosisValues = new LinkedList<>();
        if (null == names || null == ids || 0 == names.length || 0 == ids.length) {
            return null == names ? "" : Arrays.toString(names);
        }

        for (int i = 0; i < names.length; i++) {
            try {
                EmrDetailValueDTO diagnosisValue = new EmrDetailValueDTO();
                diagnosisValue.setName(names[i]);
                diagnosisValue.setCode(ids[i]);
                diagnosisValues.add(diagnosisValue);
            } catch (Exception e) {
                logger.error("EmrRecipeManager getEmrDetailValueDTO names={},ids={}", JSONUtils.toString(names), JSONUtils.toString(ids), e);
            }
        }
        return JSONUtils.toString(diagnosisValues);
    }

}
