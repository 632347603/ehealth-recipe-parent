package recipe.client;

import com.alibaba.fastjson.JSON;
import com.ngari.base.patient.service.IPatientService;
import com.ngari.patient.dto.DepartmentDTO;
import com.ngari.patient.service.DepartmentService;
import com.ngari.patient.utils.ObjectCopyUtils;
import com.ngari.recipe.dto.EmrDetailDTO;
import com.ngari.recipe.dto.EmrDetailValueDTO;
import com.ngari.recipe.entity.Recipe;
import com.ngari.recipe.entity.RecipeExtend;
import com.ngari.recipe.entity.Recipedetail;
import ctd.util.JSONUtils;
import eh.cdr.api.service.IDocIndexService;
import eh.cdr.api.vo.*;
import eh.cdr.api.vo.request.RecipeInfoReq;
import eh.cdr.api.vo.request.SaveEmrContractReq;
import eh.cdr.api.vo.response.EmrConfigRes;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import recipe.aop.LogRecord;
import recipe.constant.RecipeEmrComment;
import recipe.enumerate.type.DocIndexShowEnum;
import recipe.util.ByteUtils;
import recipe.util.DictionaryUtil;
import recipe.util.ValidateUtil;

import javax.annotation.Resource;
import java.util.*;

/**
 * 电子病历信息处理类
 *
 * @author fuzi
 */
@Service
public class DocIndexClient extends BaseClient {
    /**
     * 病历状态 2 暂存 4 已使用
     */
    private static final Integer DOC_STATUS_HOLD = 2;
    private static final Integer DOC_STATUS_USE = 4;

    /**
     * 处方类型
     */
    private static final Integer BUSS_TYPE_RECIPE = 3;

    @Resource
    private IDocIndexService docIndexService;
    @Resource
    private DepartmentService departmentService;
    @Resource
    private IConfigurationClient configurationClient;
    @Resource
    private IPatientService iPatientService;

    /**
     * 根据病历id 获取 电子病例明细对象
     *
     * @param docIndexId 电子病历id
     * @return
     */
    public MedicalDetailBean getEmrMedicalDetail(Integer docIndexId) {
        MedicalInfoBean medicalInfoBean = getEmrMedicalInfoBean(docIndexId);
        if (null == medicalInfoBean) {
            return null;
        }
        return medicalInfoBean.getMedicalDetailBean();
    }

    /**
     * 获取电子病历 明细字段 增加病例号
     *
     * @param docIndexId
     * @return
     */
    public EmrDetailDTO getEmrDetailsV1(Integer docIndexId) {
        MedicalInfoBean medicalInfoBean = getEmrMedicalInfoBean(docIndexId);
        if (null == medicalInfoBean) {
            return new EmrDetailDTO();
        }
        MedicalDetailBean medicalDetailBean = medicalInfoBean.getMedicalDetailBean();
        if (null == medicalDetailBean) {
            return new EmrDetailDTO();
        }
        List<EmrConfigRes> detailList = medicalDetailBean.getDetailList();
        if (CollectionUtils.isEmpty(detailList)) {
            return new EmrDetailDTO();
        }
        EmrDetailDTO emrDetail = getMedicalInfo(detailList);
        DocIndexBean docIndexBean = medicalInfoBean.getDocIndexBean();
        if (null != docIndexBean) {
            emrDetail.setHisEmrId(docIndexBean.getHisEmrId());
        }
        logger.info("DocIndexClient getEmrDetails docIndexId={},  emrDetail:{}", docIndexId, JSON.toJSONString(emrDetail));
        return emrDetail;
    }


    /**
     * 获取电子病历 明细字段
     *
     * @param docIndexId
     * @return
     */
    public EmrDetailDTO getEmrDetails(Integer docIndexId) {
        return getEmrDetailsV1(docIndexId);
    }


    /**
     * 根据复诊id 获取 电子病例明细对象
     *
     * @param clinicId 复诊id
     * @return
     */
    public MedicalDetailBean getEmrDetailsByClinicId(Integer clinicId, Integer bussSource) {
        if (ValidateUtil.integerIsEmpty(clinicId)) {
            return null;
        }
        //业务类型， 1 处方 2 复诊 3 检查 4 检验
        MedicalInfoBean medicalInfoBean = docIndexService.getMedicalInfoByBussTypeBussIdV2(this.bussType(bussSource), clinicId);
        logger.info("DocIndexClient getEmrDetailsByClinicId clinicId={}, medicalInfoBean:{}", clinicId, JSONUtils.toString(medicalInfoBean));
        if (null != medicalInfoBean) {
            return medicalInfoBean.getMedicalDetailBean();
        }
        return null;
    }


    /**
     * 根据老处方获取一个新的电子病历数据 有复诊就关联复诊
     *
     * @param recipe
     * @param recipeExtend
     * @param clinicId
     * @return
     */
    public MedicalDetailBean copyEmrDetails(Recipe recipe, RecipeExtend recipeExtend, Integer clinicId, Integer bussSource) {
        if (null == recipe) {
            return null;
        }
        if (null == recipeExtend || ValidateUtil.integerIsEmpty(recipeExtend.getDocIndexId())) {
            return null;
        }
        CoverMedicalInfoBean coverMedical = new CoverMedicalInfoBean();
        Integer bussType = this.bussType(bussSource);
        coverMedical.setBussType(bussType);
        coverMedical.setBussId(this.bussId(bussSource, recipe.getRecipeId(), clinicId));
        if (bussType.equals(1)) {
            coverMedical.setBussType(null);
            coverMedical.setBussId(null);
        }
        coverMedical.setOldDocIndexId(recipeExtend.getDocIndexId());
        coverMedical.setMpiid(recipe.getMpiid());
        coverMedical.setCreateOrgan(recipe.getClinicOrgan());
        coverMedical.setDepartName(DictionaryUtil.getDictionary("eh.base.dictionary.Depart", recipe.getDepart()));
        coverMedical.setDoctorName(recipe.getDoctorName());
        coverMedical.setGetDate(new Date());
        coverMedical.setDocStatus(DOC_STATUS_HOLD);
        logger.info("DocIndexClient copyEmrDetails coverMedical:{}", JSONUtils.toString(coverMedical));
        MedicalInfoBean medicalInfoBean = docIndexService.coverMedicalInfo(coverMedical);
        logger.info("DocIndexClient copyEmrDetails medicalInfoBean:{}", JSONUtils.toString(medicalInfoBean));
        if (null != medicalInfoBean) {
            return medicalInfoBean.getMedicalDetailBean();
        }
        return null;

    }


    /**
     * 将药品信息移出病历
     *
     * @param recipeId 处方
     */
    public void deleteRecipeDetailsFromDoc(Integer recipeId) {
        logger.info("DocIndexClient deleteRecipeDetailsFromDoc recipeId={}", recipeId);
        //将药品信息移出病历
        try {
            // 隐藏
            this.updateStatusByBussIdBussType(recipeId,DocIndexShowEnum.HIDE.getCode());
            docIndexService.deleteRpDetailRelation(recipeId);
        } catch (Exception e) {
            logger.warn("DocIndexClient deleteRecipeDetailsFromDoc error", e);
        }
    }

    public void updateEmrStatus(Recipe recipe, Integer docId, Integer clinicId, RecipeExtend recipeExtend) {
        //更新电子病例 为已经使用状态
        SaveEmrContractReq saveEmrContractReq = new SaveEmrContractReq();
        saveEmrContractReq.setBussId(recipe.getRecipeId());
        saveEmrContractReq.setBussType(1);
        saveEmrContractReq.setDocIndexId(docId);
        saveEmrContractReq.setRegisterNo(recipeExtend.getRegisterID());
        // 没有审核   1 显示 0 撤销
        List<String> hideRecipeDetail = configurationClient.getValueListCatch(recipe.getClinicOrgan(), "hideRecipeDetail", null);
        logger.info("updateEmrStatus 药品类型：{} 需要隐方的类型:{}", recipe.getRecipeType(), hideRecipeDetail);
        Integer code = 1;
        if (org.apache.commons.collections.CollectionUtils.isNotEmpty(hideRecipeDetail) && hideRecipeDetail.contains(recipe.getRecipeType().toString())) {
            code = 0;
        }
        saveEmrContractReq.setDocindexExtStatus(code);
        if (ValidateUtil.integerIsEmpty(clinicId)) {
            saveEmrContractReq.setDocStatus(DOC_STATUS_USE);
        }
        logger.info("EmrRecipeManager updateEmrStatus saveEmrContractReq={} ", JSON.toJSONString(saveEmrContractReq));
        Integer result = docIndexService.saveBussContact(saveEmrContractReq);
        logger.info("EmrRecipeManager updateEmrStatus docId={}result={}", docId, result);
    }

    /**
     * 写入电子病例 药品信息
     *
     * @param recipe           处方信息
     * @param recipeExtend     处方扩展
     * @param recipeDetailList 处方药品
     * @param docId            电子病历id
     */
    public void saveRpDetail(Recipe recipe, RecipeExtend recipeExtend, List<Recipedetail> recipeDetailList, Integer docId) {
        try {
            //写入电子病例 药品信息
            List<RpDetailBean> rpDetailBean = ObjectCopyUtils.convert(recipeDetailList, RpDetailBean.class);
            RecipeInfoReq recipeInfoReq = new RecipeInfoReq();
            recipeInfoReq.setRpDetailBeanList(rpDetailBean);
            recipeInfoReq.setRecipeId(recipe.getRecipeId());
            recipeInfoReq.setHisRecipeCode(recipe.getRecipeCode());
            recipeInfoReq.setDocIndexId(docId);
            if (null != recipeExtend) {
                recipeInfoReq.setRegisterNo(recipeExtend.getRegisterID());
                recipeInfoReq.setMakeMethodText(recipeExtend.getMakeMethodText());
                //TODO everyTcmNumFre 是否需要写入电子病历？？？
                recipeInfoReq.setDecoctionText(recipeExtend.getDecoctionText());
                recipeInfoReq.setJuice(recipeExtend.getJuice());
                recipeInfoReq.setJuiceUnit(recipeExtend.getJuiceUnit());
                recipeInfoReq.setMinor(recipeExtend.getMinor());
                recipeInfoReq.setMinorUnit(recipeExtend.getMinorUnit());
            }
            recipeInfoReq.setRecipeType(recipe.getRecipeType());
            recipeInfoReq.setRecipeMemo(recipe.getRecipeMemo());
            recipeInfoReq.setCopyNum(ByteUtils.objValueOfString(recipe.getCopyNum()));
            Recipedetail recipeDetail = recipeDetailList.get(0);
            recipeInfoReq.setUseDays(ByteUtils.objValueOfString(recipeDetail.getUseDays()));
            recipeInfoReq.setUsePathways(DictionaryUtil.getDictionary("eh.cdr.dictionary.UsePathways", recipeDetail.getUsePathways()));
            recipeInfoReq.setUsingRate(DictionaryUtil.getDictionary("eh.cdr.dictionary.UsingRate", recipeDetail.getUsingRate()));
            recipeInfoReq.setPharmacyName(recipeDetail.getPharmacyName());
            logger.info("EmrRecipeManager upDocIndex recipeInfoReq：{} ", JSON.toJSONString(recipeInfoReq));
            docIndexService.saveRpDetailRelation(recipeInfoReq);
        } catch (Exception e) {
            logger.error("EmrRecipeManager upDocIndex  saveRpDetailRelation error docId：{} ", docId, e);
        }
    }

    /**
     * 新增电子病历 主要用于兼容老数据
     *
     * @param recipeExt 处方扩展
     */
    public void addMedicalInfo(Recipe recipe, RecipeExtend recipeExt, String doctorName) {
        if (null == recipeExt) {
            return;
        }
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
        docIndexBean.setAppointDeptCode(recipe.getAppointDepart());
        docIndexBean.setAppointDeptName(recipe.getAppointDepartName());
        try {
            String departName = Optional.ofNullable(departmentService.get(recipe.getDepart())).map(DepartmentDTO::getName).orElse(null);
            docIndexBean.setDepartName(departName);
        } catch (Exception e) {
            logger.error("DocIndexClient addMedicalInfo departName error", e);
        }
        docIndexBean.setCreateDoctor(recipe.getDoctor());
        docIndexBean.setDoctorName(doctorName);
        docIndexBean.setCreateDate(recipe.getCreateDate());
        docIndexBean.setGetDate(new Date());
        docIndexBean.setDoctypeName("电子处方病历");
        docIndexBean.setDocStatus(DOC_STATUS_HOLD);
        docIndexBean.setDocFlag(0);
        docIndexBean.setOrganNameByUser(recipe.getOrganName());
        docIndexBean.setClinicPersonName(recipe.getPatientName());
        docIndexBean.setLastModify(new Date());
        //保存电子病历
        MedicalInfoBean medicalInfoBean = new MedicalInfoBean();
        medicalInfoBean.setDocIndexBean(docIndexBean);
        //设置病历详情
        MedicalDetailBean medicalDetailBean = new MedicalDetailBean();
        setMedicalDetailBean(recipe, recipeExt, medicalDetailBean);
        medicalInfoBean.setMedicalDetailBean(medicalDetailBean);
        logger.info("DocIndexClient addMedicalInfo  medicalDetailBean:{}", JSONUtils.toString(medicalInfoBean));
        Integer docId = docIndexService.saveMedicalInfo(medicalInfoBean);
        recipeExt.setDocIndexId(docId);
        logger.info("DocIndexClient addMedicalInfo end docId={}", docId);
    }

    /**
     * 显示
     *
     * @param bussId
     * @param docStatus
     */
    @LogRecord
    public void updateStatusByBussIdBussType(Integer bussId, Integer docStatus) {
        docIndexService.updateStatusByBussIdBussType(bussId, BUSS_TYPE_RECIPE, docStatus);
    }

    /**
     * 保存处方电子病历-电子病历端显示处方列表
     *
     * @param recipe 处方对象
     */
    @LogRecord
    public void saveRecipeDocIndex(Recipe recipe) {
        com.ngari.base.patient.model.DocIndexBean docIndex = new com.ngari.base.patient.model.DocIndexBean();
        docIndex.setDocSummary("处方");
        docIndex.setDoctypeName("处方");
        docIndex.setDocId(recipe.getRecipeId());
        docIndex.setMpiid(recipe.getMpiid());
        docIndex.setCreateOrgan(recipe.getClinicOrgan());
        docIndex.setCreateDepart(recipe.getDepart());
        docIndex.setCreateDoctor(recipe.getDoctor());
        // docStatus   0  正常（显示） 1  删除状态（不显示）
//        docIndex.setDocStatus(DocIndexShowEnum.NO_AUDIT.getCode().equals(recipe.getReviewType()) ? DocIndexShowEnum.SHOW.getCode() : DocIndexShowEnum.HIDE.getCode());
        List<String> hideRecipeDetail = configurationClient.getValueListCatch(recipe.getClinicOrgan(), "hideRecipeDetail", null);
        logger.info("saveRecipeDocIndex 药品类型：{} 需要隐方的类型:{}", recipe.getRecipeType(), hideRecipeDetail);
        Integer code = DocIndexShowEnum.SHOW.getCode();
        if (org.apache.commons.collections.CollectionUtils.isNotEmpty(hideRecipeDetail) && hideRecipeDetail.contains(recipe.getRecipeType().toString())) {
            code = DocIndexShowEnum.HIDE.getCode();
        }
        docIndex.setDocStatus(code);
        String recipeTypeText = DictionaryUtil.getDictionary("eh.cdr.dictionary.RecipeType", recipe.getRecipeType());
        docIndex.setDocTitle(recipeTypeText);
        docIndex.setDoctorName(doctorService.getNameById(recipe.getDoctor()));
        docIndex.setDepartName(departmentService.getNameById(recipe.getDepart()));
        logger.info("DocIndexClient saveRecipeDocIndex docIndex={}", JSON.toJSONString(docIndex));
        iPatientService.saveRecipeDocIndex(docIndex, "3", 3);
    }


    /**
     * 更新电子病例 开方科室医生信息
     *
     * @param docIndexId
     * @param departId
     * @param doctorId
     * @return
     */
    public Boolean updateDocIndexInfo(Integer docIndexId, Integer departId, Integer doctorId) {
        RecipeContinuationDTO recipeContinuation = new RecipeContinuationDTO();
        recipeContinuation.setDocindexId(docIndexId);
        recipeContinuation.setDepartmentId(departId);
        recipeContinuation.setDoctorId(doctorId);
        docIndexService.updateDocIndexForRecipeContinuation(recipeContinuation);
        return true;
    }


    /**
     * 设置处方默认数据
     *
     * @param recipe 处方头对象
     */
    public void setRecipeExt(Recipe recipe, RecipeExtend extend) {
        if (null == extend || ValidateUtil.integerIsEmpty(extend.getDocIndexId())) {
            return;
        }
        EmrDetailDTO emrDetailDTO = this.getEmrDetailsV1(extend.getDocIndexId());
        recipe.setOrganDiseaseName(emrDetailDTO.getOrganDiseaseName());
        recipe.setOrganDiseaseId(emrDetailDTO.getOrganDiseaseId());
    }

    private MedicalInfoBean getEmrMedicalInfoBean(Integer docIndexId) {
        if (ValidateUtil.integerIsEmpty(docIndexId)) {
            return null;
        }
        MedicalInfoBean medicalInfoBean = docIndexService.getMedicalInfoByDocIndexIdV2(docIndexId);
        logger.info("DocIndexClient getEmrDetails docIndexId={},  medicalInfoBean:{}", docIndexId, JSONUtils.toString(medicalInfoBean));
        return medicalInfoBean;
    }

    /**
     * 组织电子病历明细数据 用于调用保存接口 主要为了兼容老版本
     *
     * @param recipe
     * @param recipeExt
     * @param medicalDetailBean
     */
    private void setMedicalDetailBean(Recipe recipe, RecipeExtend recipeExt, MedicalDetailBean medicalDetailBean) {
        List<EmrConfigRes> detail = new ArrayList<>();
        //设置主诉
        detail.add(new recipe.dto.EmrDetailDTO(RecipeEmrComment.COMPLAIN, "主诉", RecipeEmrComment.TEXT_AREA, ValidateUtil.isEmpty(recipeExt.getMainDieaseDescribe()), true));
        //病史
        detail.add(new recipe.dto.EmrDetailDTO(RecipeEmrComment.MEDICAL_HISTORY, "病史", RecipeEmrComment.TEXT_AREA, ValidateUtil.isEmpty(recipeExt.getHistoryOfPresentIllness()), true));
        //设置现病史
        detail.add(new recipe.dto.EmrDetailDTO(RecipeEmrComment.CURRENT_MEDICAL_HISTORY, "现病史", RecipeEmrComment.TEXT_AREA, ValidateUtil.isEmpty(recipeExt.getCurrentMedical()), false));
        //设置既往史
        detail.add(new recipe.dto.EmrDetailDTO(RecipeEmrComment.PAST_MEDICAL_HISTORY, "既往史", RecipeEmrComment.TEXT_AREA, ValidateUtil.isEmpty(recipeExt.getHistroyMedical()), false));
        //设置过敏史
        detail.add(new recipe.dto.EmrDetailDTO(RecipeEmrComment.ALLERGY_HISTORY, "过敏史", RecipeEmrComment.TEXT_AREA, ValidateUtil.isEmpty(recipeExt.getAllergyMedical()), false));
        //设置备注
        detail.add(new recipe.dto.EmrDetailDTO(RecipeEmrComment.REMARK, "备注", RecipeEmrComment.TEXT_AREA, ValidateUtil.isEmpty(recipe.getMemo()), false));
        //设置诊断
        if (!StringUtils.isEmpty(recipe.getOrganDiseaseName())) {
            String[] diseaseNames = ByteUtils.split(recipe.getOrganDiseaseName(), ByteUtils.SEMI_COLON_EN);
            String[] diseaseIds = ByteUtils.split(recipe.getOrganDiseaseId(), ByteUtils.SEMI_COLON_EN);
            detail.add(new recipe.dto.EmrDetailDTO(RecipeEmrComment.DIAGNOSIS, "诊断", RecipeEmrComment.MULTI_SEARCH, getEmrDetailValueDTO(diseaseNames, diseaseIds), true));
        }
        //设置中医证候
        if (!StringUtils.isEmpty(recipeExt.getSymptomName())) {
            String[] symptomNames = ByteUtils.split(recipeExt.getSymptomName(), ByteUtils.SEMI_COLON_EN);
            String[] symptomIds = ByteUtils.split(recipeExt.getSymptomId(), ByteUtils.SEMI_COLON_EN);
            detail.add(new recipe.dto.EmrDetailDTO(RecipeEmrComment.TCM_SYNDROME, "中医证候", RecipeEmrComment.MULTI_SEARCH, getEmrDetailValueDTO(symptomNames, symptomIds), false));
        }
        medicalDetailBean.setDetail(JSONUtils.toString(detail));
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
        if (null == names) {
            return null;
        }
        for (int i = 0; i < names.length; i++) {
            try {
                EmrDetailValueDTO diagnosisValue = new EmrDetailValueDTO();
                if (!StringUtils.isEmpty(names[i])) {
                    diagnosisValue.setName(names[i]);
                }
                try {
                    if (null != ids) {
                        diagnosisValue.setCode(ids[i]);
                    }
                } catch (Exception e1) {
                    logger.warn("DocIndexClient getEmrDetailValueDTO ids={},mas={}", ids, e1.getMessage());
                }
                diagnosisValues.add(diagnosisValue);
            } catch (Exception e) {
                logger.error("DocIndexClient getEmrDetailValueDTO names={},ids={}", JSONUtils.toString(names), JSONUtils.toString(ids), e);
            }
        }
        return JSONUtils.toString(diagnosisValues);
    }


    /**
     * 查询电子病例字段返回
     */
    private EmrDetailDTO getMedicalInfo(List<EmrConfigRes> detailList) {
        EmrDetailDTO emrDetail = new EmrDetailDTO();
        List<EmrDetailValueDTO> diseaseClassify = new ArrayList<>();
        for (EmrConfigRes detailDTO : detailList) {
            if (null == detailDTO) {
                continue;
            }
            String value = detailDTO.getValue();
            if (StringUtils.isEmpty(value)) {
                continue;
            }
            String type = detailDTO.getType();
            if (!RecipeEmrComment.TEXT_AREA.equals(type) && !RecipeEmrComment.MULTI_SEARCH.equals(type)) {
                continue;
            }
            String key = detailDTO.getKey();
            if (RecipeEmrComment.COMPLAIN.equals(key)) {
                emrDetail.setMainDieaseDescribe(value);
                continue;
            }
            if (RecipeEmrComment.CURRENT_MEDICAL_HISTORY.equals(key)) {
                emrDetail.setCurrentMedical(value);
                continue;
            }
            if (RecipeEmrComment.PAST_MEDICAL_HISTORY.equals(key)) {
                emrDetail.setHistroyMedical(value);
                continue;
            }
            if (RecipeEmrComment.MEDICAL_HISTORY.equals(key)) {
                emrDetail.setHistoryOfPresentIllness(value);
                continue;
            }
            if (RecipeEmrComment.ALLERGY_HISTORY.equals(key)) {
                emrDetail.setAllergyMedical(value);
                continue;
            }
            if (RecipeEmrComment.PHYSICAL_EXAMINATION.equals(key)) {
                emrDetail.setPhysicalCheck(value);
                continue;
            }
            if (RecipeEmrComment.PROCESSING_METHOD.equals(key)) {
                emrDetail.setHandleMethod(value);
                continue;
            }
            if (RecipeEmrComment.REMARK.equals(key)) {
                emrDetail.setMemo(value);
                continue;
            }
            if (!RecipeEmrComment.MULTI_SEARCH.equals(type)) {
                continue;
            }
            multiSearch(detailDTO, emrDetail, diseaseClassify);
        }
        //存在类型诊断，则优先返回类型诊断
        if (!CollectionUtils.isEmpty(diseaseClassify)) {
            emrDetail.setDiseaseValue(diseaseClassify);
        }
        return emrDetail;
    }

    /**
     * 查询电子病例字段返回
     * 诊断，证候等字段处理
     */
    private void multiSearch(EmrConfigRes detailDTO, EmrDetailDTO emrDetail, List<EmrDetailValueDTO> diseaseClassify) {
        List<EmrDetailValueDTO> values = JSON.parseArray(detailDTO.getValue(), EmrDetailValueDTO.class);
        if (CollectionUtils.isEmpty(values)) {
            return;
        }
        StringBuilder names = new StringBuilder();
        StringBuilder ids = new StringBuilder();
        if (RecipeEmrComment.TCM_SYNDROME.equals(detailDTO.getKey())) {
            values.forEach(b -> {
                names.append(b.getName()).append(ByteUtils.SEMI_COLON_EN);
                ids.append(b.getCode()).append(ByteUtils.SEMI_COLON_EN);
            });
            if (!ValidateUtil.isEmpty(names)) {
                emrDetail.setSymptomName(ByteUtils.subString(names));
            }
            if (!ValidateUtil.isEmpty(ids)) {
                emrDetail.setSymptomId(ByteUtils.subString(ids));
            }
            emrDetail.setSymptomValue(values);
            return;
        }
        if (RecipeEmrComment.DIAGNOSIS.equals(detailDTO.getKey())) {
            values.forEach(b -> {
                b.setType(0);
                names.append(b.getName()).append(ByteUtils.SEMI_COLON_EN);
                ids.append(b.getCode()).append(ByteUtils.SEMI_COLON_EN);
            });
            if (!ValidateUtil.isEmpty(names)) {
                emrDetail.setOrganDiseaseName(ByteUtils.subString(names));
            }
            if (!ValidateUtil.isEmpty(ids)) {
                emrDetail.setOrganDiseaseId(ByteUtils.subString(ids));
            }
            emrDetail.setDiseaseValue(values);
            return;
        }
        if (RecipeEmrComment.DIAGNOSIS_WESTERN.equals(detailDTO.getKey())) {
            Optional.ofNullable(values).orElseGet(Collections::emptyList).forEach(a -> a.setType(1));
            diseaseClassify.addAll(values);
            return;
        }
        if (RecipeEmrComment.DIAGNOSIS_TCM.equals(detailDTO.getKey())) {
            Optional.ofNullable(values).orElseGet(Collections::emptyList).forEach(a -> a.setType(2));
            diseaseClassify.addAll(values);
        }
    }

    /**
     * 枚举转换
     *
     * @param bussSource 处方 ：开处方来源 1问诊 2复诊(在线续方) 3网络门诊 5门诊
     * @return 电子病历：1处方 2 复诊 5云门诊 8咨询
     */
    private Integer bussType(Integer bussSource) {
        if (ValidateUtil.integerIsEmpty(bussSource)) {
            return 1;
        }
        switch (bussSource) {
            case 1:
                return 8;
            case 2:
                return 2;
            default:
                return 1;
        }
    }

    private Integer bussId(Integer bussSource, Integer recipeId, Integer clinicId) {
        if (this.bussType(bussSource).equals(1)) {
            return recipeId;
        } else {
            return clinicId;
        }
    }

}
