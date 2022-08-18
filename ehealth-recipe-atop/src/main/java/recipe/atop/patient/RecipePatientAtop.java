package recipe.atop.patient;

import com.alibaba.fastjson.JSON;
import com.ngari.patient.dto.PatientDTO;
import com.ngari.recipe.dto.DiseaseInfoDTO;
import com.ngari.recipe.dto.OutPatientRecipeDTO;
import com.ngari.recipe.recipe.model.*;
import com.ngari.recipe.vo.*;
import ctd.persistence.exception.DAOException;
import ctd.util.BeanUtils;
import ctd.util.annotation.RpcBean;
import ctd.util.annotation.RpcService;
import eh.utils.ValidateUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import recipe.atop.BaseAtop;
import recipe.constant.ErrorCode;
import recipe.core.api.IRecipeBusinessService;
import recipe.core.api.patient.IPatientBusinessService;
import recipe.enumerate.status.OutRecipeStatusEnum;
import recipe.enumerate.type.DrugBelongTypeEnum;
import recipe.enumerate.type.OutRecipeGiveModeEnum;
import recipe.enumerate.type.OutRecipeRecipeTypeEnum;
import recipe.util.ObjectCopyUtils;
import recipe.vo.patient.ReadyRecipeVO;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 门诊处方服务
 *
 * @author yinsheng
 * @date 2021\7\16 0016 14:04
 */
@RpcBean(value = "outRecipePatientAtop")
public class RecipePatientAtop extends BaseAtop {

    @Autowired
    private IRecipeBusinessService recipeBusinessService;

    @Autowired
    private IPatientBusinessService recipePatientService;

    /**
     * 查询门诊处方信息
     *
     * @param outPatientRecipeReqVO 患者信息
     * @return 门诊处方列表
     */
    @RpcService
    public List<OutPatientRecipeVO> queryOutPatientRecipe(OutPatientRecipeReqVO outPatientRecipeReqVO) {
        validateAtop(outPatientRecipeReqVO, outPatientRecipeReqVO.getOrganId(), outPatientRecipeReqVO.getMpiId());
        try {
            //设置默认查询时间3个月
            //outPatientRecipeReqVO.setBeginTime(DateConversion.getDateFormatter(DateConversion.getMonthsAgo(3), DateConversion.DEFAULT_DATE_TIME));
            //outPatientRecipeReqVO.setEndTime(DateConversion.getDateFormatter(new Date(), DateConversion.DEFAULT_DATE_TIME));
            PatientDTO patientDTO = recipePatientService.getPatientDTOByMpiID(outPatientRecipeReqVO.getMpiId());
            outPatientRecipeReqVO.setIdCard(StringUtils.isNotEmpty(outPatientRecipeReqVO.getIdCard()) ? outPatientRecipeReqVO.getIdCard() : patientDTO.getCertificate());
            outPatientRecipeReqVO.setPatientId(patientDTO.getPatId());
            logger.info("OutPatientRecipeAtop queryOutPatientRecipe outPatientRecipeReq:{}.", JSON.toJSONString(outPatientRecipeReqVO));
            //获取线下门诊处方
            List<OutPatientRecipeDTO> outPatientRecipeDTOS = recipeBusinessService.queryOutPatientRecipe(outPatientRecipeReqVO);
            //按照开方时间倒序
            outPatientRecipeDTOS = outPatientRecipeDTOS.stream().sorted(Comparator.comparing(OutPatientRecipeDTO::getCreateDate).reversed()).collect(Collectors.toList());
            //包装前端展示信息
            final List<OutPatientRecipeVO> result = new ArrayList<>();
            outPatientRecipeDTOS.forEach(outPatientRecipeDTO -> {
                OutPatientRecipeVO outPatientRecipeVO = new OutPatientRecipeVO();
                BeanUtils.copy(outPatientRecipeDTO, outPatientRecipeVO);
                outPatientRecipeVO.setStatusText(OutRecipeStatusEnum.getName(outPatientRecipeVO.getStatus()));
                outPatientRecipeVO.setGiveModeText(OutRecipeGiveModeEnum.getName(outPatientRecipeVO.getGiveMode()));
                outPatientRecipeVO.setRecipeTypeText(OutRecipeRecipeTypeEnum.getName(outPatientRecipeVO.getRecipeType()));
                outPatientRecipeVO.setOrganId(outPatientRecipeReqVO.getOrganId());
                outPatientRecipeVO.setMpiId(outPatientRecipeReqVO.getMpiId());
                if (StringUtils.isEmpty(outPatientRecipeVO.getOrganName())) {
                    outPatientRecipeVO.setOrganName(outPatientRecipeReqVO.getOrganName());
                }
                List<OutPatientRecipeDetailVO> outPatientRecipeDetailVOList = ObjectCopyUtils.convert(outPatientRecipeDTO.getOutPatientRecipeDetails(), OutPatientRecipeDetailVO.class);
                Boolean haveSecrecyDrugFlag = outPatientRecipeDetailVOList.stream().anyMatch(outPatientRecipeDetail -> DrugBelongTypeEnum.SECRECY_DRUG.getType().equals(outPatientRecipeDetail.getType()));
                if (haveSecrecyDrugFlag) {
                    BigDecimal offlineRecipeTotalPrice = outPatientRecipeDetailVOList.stream().filter(outPatientRecipeDetail -> DrugBelongTypeEnum.SECRECY_DRUG.getType().equals(outPatientRecipeDetail.getType())).map(outPatientRecipeDetailDTO -> new BigDecimal(Double.parseDouble(outPatientRecipeDetailDTO.getTotalPrice()))).reduce(BigDecimal.ZERO, BigDecimal::add);
                    outPatientRecipeVO.setOfflineRecipeTotalPrice(offlineRecipeTotalPrice);
                }
                outPatientRecipeVO.setOfflineRecipeName(outPatientRecipeDTO.getOfflineRecipeName());
                result.add(outPatientRecipeVO);
            });
            logger.info("OutPatientRecipeAtop queryOutPatientRecipe result:{}.", JSON.toJSONString(result));
            return result;
        } catch (DAOException e1) {
            logger.error("OutPatientRecipeAtop queryOutPatientRecipe DAOException error", e1);
            return new ArrayList<>();
        } catch (Exception e) {
            logger.error("OutPatientRecipeAtop queryOutPatientRecipe Exception error e", e);
            throw new DAOException(ErrorCode.SERVICE_ERROR, e.getMessage());
        }
    }

    /**
     * 获取线下门诊处方诊断信息
     *
     * @param patientInfoVO 患者信息
     * @return 诊断列表
     */
    @RpcService
    public String getOutRecipeDisease(PatientInfoVO patientInfoVO) {
        logger.info("OutPatientRecipeAtop getOutRecipeDisease patientInfoVO:{}.", JSON.toJSONString(patientInfoVO));
        validateAtop(patientInfoVO, patientInfoVO.getOrganId(), patientInfoVO.getPatientName(), patientInfoVO.getPatientId(), patientInfoVO.getRegisterID());
        try {
            List<DiseaseInfoDTO> result = recipeBusinessService.getOutRecipeDisease(patientInfoVO);
            final StringBuilder diseaseNames = new StringBuilder();
            result.forEach(diseaseInfoDTO -> diseaseNames.append(diseaseInfoDTO.getDiseaseName()).append(";"));
            if (StringUtils.isNotEmpty(diseaseNames)) {
                diseaseNames.deleteCharAt(diseaseNames.lastIndexOf(";"));
            }
            logger.info("OutPatientRecipeAtop getOutRecipeDisease diseaseNames = {}", diseaseNames);
            return diseaseNames.toString();
        } catch (Exception e) {
            logger.error("OutPatientRecipeAtop getOutRecipeDisease error e", e);
            return "";
        }
    }

    /**
     * 获取门诊处方详情信息
     *
     * @param outRecipeDetailReqVO 门诊处方信息
     * @return 图片或者PDF链接等
     */
    @RpcService
    public OutRecipeDetailVO queryOutRecipeDetail(OutRecipeDetailReqVO outRecipeDetailReqVO) {
        logger.info("OutPatientRecipeAtop getOutRecipeDisease queryOutRecipeDetail:{}.", JSON.toJSONString(outRecipeDetailReqVO));
        try {
            OutRecipeDetailVO result = recipeBusinessService.queryOutRecipeDetail(outRecipeDetailReqVO);
            logger.info("OutPatientRecipeAtop queryOutRecipeDetail result = {}", result);
            return result;
        } catch (DAOException e1) {
            logger.error("OutPatientRecipeAtop queryOutRecipeDetail error", e1);
            throw new DAOException(ErrorCode.SERVICE_ERROR, e1.getMessage());
        } catch (Exception e) {
            logger.error("OutPatientRecipeAtop queryOutRecipeDetail error e", e);
            throw new DAOException(ErrorCode.SERVICE_ERROR, e.getMessage());
        }
    }

    /**
     * 前端获取用药指导
     *
     * @param medicationGuidanceReqVO 用药指导入参
     * @return 用药指导出参
     */
    @RpcService
    public MedicationGuideResVO getMedicationGuide(MedicationGuidanceReqVO medicationGuidanceReqVO) {
        logger.info("OutPatientRecipeAtop getMedicationGuide medicationGuidanceReqVO:{}.", JSON.toJSONString(medicationGuidanceReqVO));
        try {
            medicationGuidanceReqVO.setOrganDiseaseId(medicationGuidanceReqVO.getOrganDiseaseName());
            MedicationGuideResVO result = recipeBusinessService.getMedicationGuide(medicationGuidanceReqVO);
            logger.info("OutPatientRecipeAtop getMedicationGuide result = {}", JSON.toJSONString(result));
            return result;
        } catch (DAOException e1) {
            logger.error("OutPatientRecipeAtop getMedicationGuide error", e1);
            throw new DAOException(ErrorCode.SERVICE_ERROR, e1.getMessage());
        } catch (Exception e) {
            logger.error("OutPatientRecipeAtop getMedicationGuide error e", e);
            throw new DAOException(ErrorCode.SERVICE_ERROR, e.getMessage());
        }
    }

    /**
     * 获取患者医保信息
     *
     * @param patientInfoVO 患者信息
     * @return 医保类型相关
     */
    @RpcService
    public PatientMedicalTypeVO queryPatientMedicalType(PatientInfoVO patientInfoVO) {
        logger.info("OutPatientRecipeAtop queryPatientMedicalType patientInfoVO:{}.", JSON.toJSONString(patientInfoVO));
        validateAtop(patientInfoVO, patientInfoVO.getOrganId(), patientInfoVO.getMpiId());
        if (ValidateUtil.nullOrZeroInteger(patientInfoVO.getClinicId())) {
            return new PatientMedicalTypeVO("1", "自费");
        }
        try {
            PatientMedicalTypeVO result = recipePatientService.queryPatientMedicalType(patientInfoVO);
            logger.info("OutPatientRecipeAtop queryPatientMedicalType result = {}", JSON.toJSONString(result));
            return result;
        } catch (DAOException e1) {
            logger.error("OutPatientRecipeAtop queryPatientMedicalType error", e1);
            throw new DAOException(ErrorCode.SERVICE_ERROR, e1.getMessage());
        } catch (Exception e) {
            logger.error("OutPatientRecipeAtop queryPatientMedicalType error e", e);
            throw new DAOException(ErrorCode.SERVICE_ERROR, e.getMessage());
        }
    }

    /**
     * 获取中药模板处方
     *
     * @param formWorkRecipeReqVO
     * @return
     */
    @RpcService
    public List<FormWorkRecipeVO> findFormWorkRecipe(FormWorkRecipeReqVO formWorkRecipeReqVO) {
        validateAtop(formWorkRecipeReqVO, formWorkRecipeReqVO.getOrganId());
        return recipePatientService.findFormWorkRecipe(formWorkRecipeReqVO);
    }


    /**
     * 是否有待处理处方
     *
     * @param orderId 订单号
     * @return
     */
    @RpcService
    public ReadyRecipeVO skipReadyRecipe(Integer orderId) {
        return recipePatientService.getReadyRecipeFlag(orderId);
    }

}
