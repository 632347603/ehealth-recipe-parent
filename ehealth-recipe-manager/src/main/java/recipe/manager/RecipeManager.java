package recipe.manager;

import com.alibaba.fastjson.JSON;
import com.ngari.recipe.dto.*;
import com.ngari.recipe.entity.Recipe;
import com.ngari.recipe.entity.RecipeExtend;
import com.ngari.recipe.entity.RecipeLog;
import com.ngari.revisit.common.model.RevisitExDTO;
import ctd.persistence.DAOFactory;
import ctd.util.JSONUtils;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import recipe.client.*;
import recipe.common.CommonConstant;
import recipe.constant.RecipeStatusConstant;
import recipe.dao.RecipeLogDAO;
import recipe.dao.RecipeRefundDAO;
import recipe.enumerate.type.RecipeShowQrConfigEnum;
import recipe.util.DictionaryUtil;
import recipe.util.ValidateUtil;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;

/**
 * 处方
 *
 * @author yinsheng
 * @date 2021\6\30 0030 14:21
 */
@Service
public class RecipeManager extends BaseManager {
    @Autowired
    private PatientClient patientClient;
    @Autowired
    private DocIndexClient docIndexClient;
    @Resource
    private IConfigurationClient configurationClient;
    @Resource
    private OfflineRecipeClient offlineRecipeClient;
    @Autowired
    private RevisitClient revisitClient;
    @Autowired
    private RecipeRefundDAO recipeRefundDAO;

    /**
     * 保存处方信息
     *
     * @param recipe 处方信息
     * @return
     */
    public Recipe saveRecipe(Recipe recipe) {
        recipe.setCreateDate(new Date());
        if (ValidateUtil.integerIsEmpty(recipe.getRecipeId())) {
            recipe = recipeDAO.save(recipe);
        } else {
            recipe = recipeDAO.update(recipe);
        }
        logger.info("RecipeManager saveRecipe recipe:{}", JSONUtils.toString(recipe));
        return recipe;
    }

    /**
     * 保存处方扩展信息
     *
     * @param recipeExtend 处方扩展信息
     * @param recipe       处方信息
     * @return
     */
    public RecipeExtend saveRecipeExtend(RecipeExtend recipeExtend, Recipe recipe) {
        if (ValidateUtil.integerIsEmpty(recipeExtend.getRecipeId())) {
            recipeExtend.setRecipeId(recipe.getRecipeId());
            recipeExtend = recipeExtendDAO.save(recipeExtend);
        } else {
            recipeExtend = recipeExtendDAO.update(recipeExtend);
        }
        logger.info("RecipeManager saveRecipeExtend recipeExtend:{}", JSONUtils.toString(recipeExtend));
        return recipeExtend;
    }


    /**
     * 获取处方信息
     *
     * @param recipeCode
     * @param clinicOrgan
     * @return
     */
    public Recipe getByRecipeCodeAndClinicOrgan(String recipeCode, Integer clinicOrgan) {
        logger.info("RecipeManager getByRecipeCodeAndClinicOrgan param recipeCode:{},clinicOrgan:{}", recipeCode, clinicOrgan);
        Recipe recipe = recipeDAO.getByRecipeCodeAndClinicOrganWithAll(recipeCode, clinicOrgan);
        logger.info("RecipeManager getByRecipeCodeAndClinicOrgan res recipe:{}", JSONUtils.toString(recipe));
        return recipe;
    }

    public Recipe getRecipeById(Integer recipeId) {
        return recipeDAO.getByRecipeId(recipeId);
    }

    /**
     * 通过recipeCode批量获取处方信息
     *
     * @param recipeCodeList
     * @param clinicOrgan
     * @return
     */
    public List<Recipe> findByRecipeCodeAndClinicOrgan(List<String> recipeCodeList, Integer clinicOrgan) {
        logger.info("RecipeManager findByRecipeCodeAndClinicOrgan param recipeCodeList:{},clinicOrgan:{}", JSONUtils.toString(recipeCodeList), clinicOrgan);
        List<Recipe> recipes = recipeDAO.findByRecipeCodeAndClinicOrgan(recipeCodeList, clinicOrgan);
        logger.info("RecipeManager findByRecipeCodeAndClinicOrgan res recipes:{}", JSONUtils.toString(recipes));
        return recipes;
    }

    public List<Recipe> findByRecipeIds(List<Integer> recipeIds) {
        List<Recipe> recipes = recipeDAO.findByRecipeIds(recipeIds);
        logger.info("RecipeManager findByRecipeIds recipeIds:{}, recipes:{}", JSON.toJSONString(recipeIds), JSON.toJSONString(recipes));
        return recipes;
    }


    /**
     * 根据业务类型(咨询/复诊)和业务单号(咨询/复诊单号)获取处方信息
     *
     * @param bussSource 咨询/复诊
     * @param clinicId   咨询/复诊单号
     * @return 处方列表
     */
    public List<Recipe> findWriteHisRecipeByBussSourceAndClinicId(Integer bussSource, Integer clinicId) {
        logger.info("RecipeManager findWriteHisRecipeByBussSourceAndClinicId param bussSource:{},clinicId:{}", bussSource, clinicId);
        List<Recipe> recipes = recipeDAO.findWriteHisRecipeByBussSourceAndClinicId(bussSource, clinicId);
        logger.info("RecipeManager findWriteHisRecipeByBussSourceAndClinicId recipes:{}.", JSON.toJSONString(recipes));
        return recipes;
    }

    /**
     * 获取有效的处方单
     *
     * @param bussSource
     * @param clinicId
     * @return
     */
    public List<Recipe> findEffectiveRecipeByBussSourceAndClinicId(Integer bussSource, Integer clinicId) {
        logger.info("RecipeManager findRecipeByBussSourceAndClinicId param bussSource:{},clinicId:{}", bussSource, clinicId);
        List<Recipe> recipes = recipeDAO.findEffectiveRecipeByBussSourceAndClinicId(bussSource, clinicId);
        logger.info("RecipeManager findEffectiveRecipeByBussSourceAndClinicId recipes:{}.", JSON.toJSONString(recipes));
        return recipes;
    }

    /**
     * 获取诊疗处方
     *
     * @param bussSource 业务类型
     * @param clinicId   业务单号
     * @return 处方列表
     */
    public List<Recipe> findTherapyRecipeByBussSourceAndClinicId(Integer bussSource, Integer clinicId) {
        logger.info("RecipeManager findTherapyRecipeByBussSourceAndClinicId param bussSource:{},clinicId:{}", bussSource, clinicId);
        List<Recipe> recipes = recipeDAO.findTherapyRecipeByBussSourceAndClinicId(bussSource, clinicId);
        logger.info("RecipeManager findTherapyRecipeByBussSourceAndClinicId recipes:{}.", JSON.toJSONString(recipes));
        return recipes;
    }


    /**
     * 获取处方相关信息 并且 字典转换
     *
     * @param recipeId 处方id
     * @return
     */
    public RecipeInfoDTO getRecipeInfoDictionary(Integer recipeId) {
        RecipeInfoDTO recipeInfoDTO = getRecipeInfoDTO(recipeId);
        PatientDTO patientBean = recipeInfoDTO.getPatientBean();
        if (StringUtils.isNotEmpty(patientBean.getPatientSex())) {
            patientBean.setPatientSex(DictionaryUtil.getDictionary("eh.base.dictionary.Gender", String.valueOf(patientBean.getPatientSex())));
        }
        return recipeInfoDTO;
    }

    /**
     * 获取处方相关信息
     *
     * @param recipeId 处方id
     * @return
     */
    public RecipeInfoDTO getRecipeInfoDTO(Integer recipeId) {
        RecipeDTO recipeDTO = getRecipeDTO(recipeId);
        RecipeInfoDTO recipeInfoDTO = new RecipeInfoDTO();
        BeanUtils.copyProperties(recipeDTO, recipeInfoDTO);
        Recipe recipe = recipeInfoDTO.getRecipe();
        PatientDTO patientBean = patientClient.getPatientEncipher(recipe.getMpiid());
        recipeInfoDTO.setPatientBean(patientBean);
        logger.info("RecipeOrderManager getRecipeInfoDTO patientBean:{}", JSON.toJSONString(patientBean));
        return recipeInfoDTO;
    }

    /**
     * 获取处方相关信息 补全数据
     *
     * @param recipeId 处方id
     * @return
     */
    @Override
    public RecipeDTO getRecipeDTO(Integer recipeId) {
        RecipeDTO recipeDTO = super.getRecipeDTO(recipeId);
        RecipeExtend recipeExtend = recipeDTO.getRecipeExtend();
        if (null == recipeExtend) {
            return recipeDTO;
        }
        recipeExtend.setCardTypeName(DictionaryUtil.getDictionary("eh.mpi.dictionary.CardType", recipeExtend.getCardType()));
        Integer docIndexId = recipeExtend.getDocIndexId();
        EmrDetailDTO emrDetail = docIndexClient.getEmrDetails(docIndexId);
        if (null == emrDetail) {
            return recipeDTO;
        }
        Recipe recipe = recipeDTO.getRecipe();
        recipe.setOrganDiseaseId(emrDetail.getOrganDiseaseId());
        recipe.setOrganDiseaseName(emrDetail.getOrganDiseaseName());
        recipe.setMemo(emrDetail.getMemo());
        recipeExtend.setSymptomId(emrDetail.getSymptomId());
        recipeExtend.setSymptomName(emrDetail.getSymptomName());
        recipeExtend.setAllergyMedical(emrDetail.getAllergyMedical());
        if (!ValidateUtil.integerIsEmpty(recipe.getClinicId()) && StringUtils.isEmpty(recipeExtend.getCardNo())) {
            RevisitExDTO consultExDTO = revisitClient.getByClinicId(recipe.getClinicId());
            if (null != consultExDTO) {
                recipeExtend.setCardNo(consultExDTO.getCardId());
                recipeExtend.setCardType(consultExDTO.getCardType());
            }
        }
        return recipeDTO;
    }


    /**
     * 获取到院取药凭证
     *
     * @param recipe       处方信息
     * @param recipeExtend 处方扩展信息
     * @return 取药凭证
     */
    public String getToHosProof(Recipe recipe, RecipeExtend recipeExtend) {
        String qrName = "";
        try {
            Integer qrTypeForRecipe = configurationClient.getValueCatchReturnInteger(recipe.getClinicOrgan(), "getQrTypeForRecipe", 1);
            RecipeShowQrConfigEnum qrConfigEnum = RecipeShowQrConfigEnum.getEnumByType(qrTypeForRecipe);
            switch (qrConfigEnum) {
                case NO_HAVE:
                    break;
                case CARD_NO:
                    //就诊卡号
                    if (StringUtils.isNotEmpty(recipeExtend.getCardNo())) {
                        qrName = recipeExtend.getCardNo();
                    }
                    break;
                case REGISTER_ID:
                    if (StringUtils.isNotEmpty(recipeExtend.getRegisterID())) {
                        qrName = recipeExtend.getRegisterID();
                    }
                    break;
                case PATIENT_ID:
                    if (StringUtils.isNotEmpty(recipe.getPatientID())) {
                        qrName = recipe.getPatientID();
                    }
                    break;
                case RECIPE_CODE:
                    if (StringUtils.isNotEmpty(recipe.getRecipeCode())) {
                        qrName = recipe.getRecipeCode();
                    }
                    break;
                case SERIALNUMBER:
                    qrName = offlineRecipeClient.queryRecipeSerialNumber(recipe.getClinicOrgan(), recipe.getPatientName(), recipe.getPatientID(), recipeExtend.getRegisterID());
                default:
                    break;
            }
        } catch (Exception e) {
            logger.error("RecipeManager getToHosProof error", e);
        }
        return qrName;
    }

    /**
     * 获取医生撤销处方时间和原因
     *
     * @param recipeId
     * @return
     */
    public RecipeCancel getCancelReasonForPatient(int recipeId) {
        RecipeCancel recipeCancel = new RecipeCancel();
        String cancelReason = "";
        Date cancelDate = null;
        RecipeLogDAO recipeLogDAO = DAOFactory.getDAO(RecipeLogDAO.class);
        List<RecipeLog> recipeLogs = recipeLogDAO.findByRecipeIdAndAfterStatus(recipeId, RecipeStatusConstant.REVOKE);
        if (CollectionUtils.isNotEmpty(recipeLogs)) {
            cancelReason = recipeLogs.get(0).getMemo();
            cancelDate = recipeLogs.get(0).getModifyDate();
        }
        recipeCancel.setCancelDate(cancelDate);
        recipeCancel.setCancelReason(cancelReason);
        logger.info("getCancelReasonForPatient recipeCancel:{}", JSONUtils.toString(recipeCancel));
        return recipeCancel;
    }

    /**
     * 更新推送his返回信息处方数据
     *
     * @param recipeResult 处方结果
     * @param recipeId     处方id
     * @param pushType     推送类型: 1：提交处方，2:撤销处方
     */
    public void updatePushHisRecipe(Recipe recipeResult, Integer recipeId, Integer pushType) {
        if (null == recipeResult) {
            return;
        }
        if (!CommonConstant.THERAPY_RECIPE_PUSH_TYPE.equals(pushType)) {
            return;
        }
        Recipe updateRecipe = new Recipe();
        updateRecipe.setRecipeId(recipeId);
        updateRecipe.setPatientID(recipeResult.getPatientID());
        updateRecipe.setRecipeCode(recipeResult.getRecipeCode());
        recipeDAO.updateNonNullFieldByPrimaryKey(updateRecipe);
        logger.info("RecipeManager updatePushHisRecipe updateRecipe:{}.", JSON.toJSONString(updateRecipe));
    }

    /**
     * 更新推送his返回信息处方扩展数据
     *
     * @param recipeExtendResult 处方扩展结果
     * @param recipeId           处方id
     * @param pushType           推送类型: 1：提交处方，2:撤销处方
     */
    public void updatePushHisRecipeExt(RecipeExtend recipeExtendResult, Integer recipeId, Integer pushType) {
        if (null == recipeExtendResult) {
            return;
        }
        if (!CommonConstant.THERAPY_RECIPE_PUSH_TYPE.equals(pushType)) {
            return;
        }
        RecipeExtend updateRecipeExt = new RecipeExtend();
        updateRecipeExt.setRecipeId(recipeId);
        updateRecipeExt.setRegisterID(recipeExtendResult.getRegisterID());
        updateRecipeExt.setMedicalType(recipeExtendResult.getMedicalType());
        updateRecipeExt.setMedicalTypeText(recipeExtendResult.getMedicalTypeText());
        updateRecipeExt.setRecipeCostNumber(recipeExtendResult.getRecipeCostNumber());
        updateRecipeExt.setHisDiseaseSerial(recipeExtendResult.getHisDiseaseSerial());
        recipeExtendDAO.updateNonNullFieldByPrimaryKey(updateRecipeExt);
        logger.info("RecipeManager updatePushHisRecipeExt updateRecipeExt:{}.", JSON.toJSONString(updateRecipeExt));
    }
}
