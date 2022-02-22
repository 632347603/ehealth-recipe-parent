package recipe.client;

import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import com.ngari.base.currentuserinfo.model.SimpleWxAccountBean;
import com.ngari.base.currentuserinfo.service.ICurrentUserInfoService;
import com.ngari.base.patient.model.HealthCardBean;
import com.ngari.base.patient.service.IPatientService;
import com.ngari.bus.op.service.IUsePathwaysService;
import com.ngari.common.mode.HisResponseTO;
import com.ngari.follow.service.IMedicineRemindService;
import com.ngari.follow.vo.MedicineRemindTO;
import com.ngari.his.patient.mode.PatientQueryRequestTO;
import com.ngari.jgpt.zjs.service.IMinkeOrganService;
import com.ngari.patient.dto.HealthCardDTO;
import com.ngari.patient.dto.OrganDTO;
import com.ngari.patient.service.HealthCardService;
import com.ngari.patient.service.OrganService;
import com.ngari.patient.service.PatientService;
import com.ngari.recipe.dto.PatientDTO;
import com.ngari.recipe.dto.RecipeInfoDTO;
import com.ngari.recipe.entity.Recipedetail;
import ctd.persistence.exception.DAOException;
import ctd.util.JSONUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import recipe.util.ChinaIDNumberUtil;
import recipe.util.DateConversion;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 患者信息处理类
 *
 * @author fuzi
 */
@Service
public class PatientClient extends BaseClient {
    @Resource
    private PatientService patientService;
    @Resource
    private IPatientService iPatientService;
    @Resource
    private OrganService organService;
    @Resource
    private IMinkeOrganService minkeOrganService;
    @Autowired
    private HealthCardService healthCardService;
    @Autowired
    private ICurrentUserInfoService currentUserInfoService;
    @Autowired
    private IMedicineRemindService medicineRemindService;
    @Autowired
    private IUsePathwaysService usePathwaysService;

    /**
     * 获取 脱敏后的 患者对象
     *
     * @param mpiId
     * @return
     */
    public PatientDTO getPatientEncipher(String mpiId) {
        com.ngari.patient.dto.PatientDTO patient = patientService.get(mpiId);
        return getPatientEncipher(patient);
    }


    /**
     * 获取患者信息
     *
     * @param mpiId
     * @return
     */
    public PatientDTO getPatientDTO(String mpiId) {
        com.ngari.patient.dto.PatientDTO patient = patientService.get(mpiId);
        PatientDTO p = new PatientDTO();
        BeanUtils.copyProperties(patient, p);
        return p;
    }

    /**
     * 健康卡获取
     *
     * @param mpiId
     * @param organId
     * @return
     */
    public HealthCardBean getCardBean(String mpiId, Integer organId) {
        try {
            return iPatientService.getHealthCard(mpiId, organId, "2");
        } catch (Exception e) {
            logger.error("RecipeHisService recipeRefund 健康卡获取失败 error", e);
            return null;
        }
    }


    /**
     * 获取脱敏患者对象
     *
     * @param mpiIds
     * @return
     */
    public Map<String, PatientDTO> findPatientMap(List<String> mpiIds) {
        List<com.ngari.patient.dto.PatientDTO> patientList = patientService.findByMpiIdIn(mpiIds);
        logger.info("PatientClient findPatientMap patientList:{}", JSON.toJSONString(patientList));
        if (CollectionUtils.isEmpty(patientList)) {
            return null;
        }
        List<PatientDTO> patientDTOList = new LinkedList<>();
        patientList.forEach(a -> patientDTOList.add(getPatientEncipher(a)));
        return patientDTOList.stream().collect(Collectors.toMap(PatientDTO::getMpiId, a -> a, (k1, k2) -> k1));
    }


    /**
     * 根据平台机构id获取民科机构登记号
     *
     * @param organId 机构id
     * @return
     */
    public String getMinkeOrganCodeByOrganId(Integer organId) {
        if (null == organId) {
            return null;
        }
        try {
            //获取民科机构登记号
            OrganDTO organDTO = organService.getByOrganId(organId);
            if (organDTO != null && StringUtils.isNotEmpty(organDTO.getMinkeUnitID())) {
                return minkeOrganService.getRegisterNumberByUnitId(organDTO.getMinkeUnitID());
            }
        } catch (Exception e) {
            logger.error("PatientClient getMinkeOrganCodeByOrganId error", e);
        }
        return null;
    }

    /**
     * 根据mpiid获取患者信息
     *
     * @param mpiId
     * @return
     */
    public com.ngari.patient.dto.PatientDTO getPatientBeanByMpiId(String mpiId) {
        if (StringUtils.isEmpty(mpiId)) {
            return null;
        }
        return patientService.getPatientBeanByMpiId(mpiId);
    }

    /**
     * 查询线下患者信息
     *
     * @param patientQueryRequestTO
     * @return
     */
    public PatientQueryRequestTO queryPatient(PatientQueryRequestTO patientQueryRequestTO) {
        logger.info("PatientClient queryPatient patientQueryRequestTO:{}.", JSON.toJSONString(patientQueryRequestTO));
        try {
            HisResponseTO<PatientQueryRequestTO> response = patientHisService.queryPatient(patientQueryRequestTO);
            PatientQueryRequestTO result = getResponse(response);
            if (result == null) {
                return null;
            }
            result.setCardID(null);
            result.setCertificate(null);
            result.setGuardianCertificate(null);
            result.setMobile(null);
            return result;
        } catch (Exception e) {
            logger.error("PatientClient queryPatient error", e);
            return null;
        }
    }


    /**
     * todo  与 queryPatient 合成一个接口
     * 判断是否是医保患者
     *
     * @return
     */
    public Boolean isMedicarePatient(Integer organId, String mpiId) {
        //获取his患者信息判断是否医保患者
        PatientQueryRequestTO req = new PatientQueryRequestTO();
        req.setOrgan(organId);
        PatientDTO patient = getPatientDTO(mpiId);
        req.setPatientName(patient.getPatientName());
        req.setCertificateType(patient.getCertificateType());
        req.setCertificate(patient.getCertificate());
        try {
            HisResponseTO<PatientQueryRequestTO> response = patientHisService.queryPatient(req);
            PatientQueryRequestTO result = getResponse(response);
            if (result != null && "2".equals(result.getPatientType())) {
                return true;
            }
        } catch (Exception e) {
            logger.error("PatientClient isMedicarePatient error ", e);
            throw new DAOException(eh.base.constant.ErrorCode.SERVICE_ERROR, "查询患者信息异常，请稍后重试");
        }
        return false;
    }

    /**
     * 获取当前患者所有家庭成员(包括自己)
     *
     * @param mpiId 当前就诊人
     * @return 所有就诊人
     */
    public List<String> getAllMemberPatientsByCurrentPatient(String mpiId) {
        logger.info("getAllMemberPatientsByCurrentPatient mpiId:{}.", mpiId);
        List<String> allMpiIds = Lists.newArrayList();
        String loginId = patientService.getLoginIdByMpiId(mpiId);
        if (StringUtils.isNotEmpty(loginId)) {
            allMpiIds = patientService.findMpiIdsByLoginId(loginId);
        }
        logger.info("getAllMemberPatientsByCurrentPatient allMpiIds:{}.", JSON.toJSONString(allMpiIds));
        return allMpiIds;
    }


    /**
     * 患者信息脱敏
     *
     * @param patient
     * @return
     */
    private PatientDTO getPatientEncipher(com.ngari.patient.dto.PatientDTO patient) {
        PatientDTO p = new PatientDTO();
        BeanUtils.copyProperties(patient, p);
        if (StringUtils.isNotEmpty(p.getMobile())) {
            //p.setMobile(LocalStringUtil.coverMobile(p.getMobile()));
        }
        if (StringUtils.isNotEmpty(p.getIdcard())) {
            p.setIdcard(ChinaIDNumberUtil.hideIdCard(p.getIdcard()));
        }
        if (null != p.getCertificateType() && 1 == p.getCertificateType() && StringUtils.isNotEmpty(p.getCertificate())) {
            p.setCertificate(ChinaIDNumberUtil.hideIdCard(p.getCertificate()));
        }
        p.setAge(null == p.getBirthday() ? 0 : DateConversion.getAge(p.getBirthday()));
        p.setIdcard2(null);
        p.setPhoto(null == p.getPhoto() ? "" : p.getPhoto());
        return p;
    }

    /**
     * 获取健康卡
     *
     * @param mpiId 患者唯一号
     * @return 健康卡列表
     */
    public Set<String> findHealthCard(String mpiId) {
        logger.info("PatientClient findHealthCard mpiId:{}.", mpiId);
        List<HealthCardDTO> healthCards = healthCardService.findByMpiId(mpiId);
        Set<String> result = healthCards.stream().map(HealthCardDTO::getCardId).collect(Collectors.toSet());
        logger.info("PatientClient findHealthCard result:{}.", JSONUtils.toString(result));
        return result;
    }

    /**
     * 根据用户信息获取所属公众号相关信息
     *
     * @return
     */
    public String getOpenId() {
        try {
            SimpleWxAccountBean simpleWxAccountBean = currentUserInfoService.getSimpleWxAccount();
            String openId = simpleWxAccountBean.getOpenId();
            logger.info("PatientClient getOpenId:{}", openId);
            return openId;
        } catch (Exception e) {
            logger.error("getOpenId error", e);
        }
        return null;
    }

    public List<HealthCardDTO> queryCardsByParam(Integer organId, String mpiId, List<String> cardTypes) throws Exception {
        logger.info("PatientClient queryCardsByParam organId:{},mpiId:{},cardTypes:{}", JSONUtils.toString(organId), mpiId, JSONUtils.toString(cardTypes));
        return healthCardService.queryCardsByParam(organId, mpiId, cardTypes);
    }

    public List<com.ngari.patient.dto.PatientDTO> patientByIdCard(PatientDTO patientDTO) {
        if (null == patientDTO) {
            return null;
        }
        String idCard = patientDTO.getIdcard();
        if (StringUtils.isEmpty(idCard)) {
            return null;
        }
        //数据转换
        List<com.ngari.patient.dto.PatientDTO> patientList = patientService.findByIdCard(idCard);
        logger.info("PatientClient patientByIdCard patientList:{}", JSON.toJSONString(patientList));
        if (CollectionUtils.isEmpty(patientList)) {
            return null;
        }
        return patientList;
    }

    /**
     * 提醒患者用药
     *
     * @param recipeInfoDTOList 处方信息
     * @return 是否成功
     */
    public Boolean remindPatientTakeMedicine(List<RecipeInfoDTO> recipeInfoDTOList) {
        logger.info("PatientClient remindPatientTakeMedicine recipeInfoDTOList:{}.", JSONUtils.toString(recipeInfoDTOList));
        List<MedicineRemindTO> medicineRemindTOList = new ArrayList<>();

        for (RecipeInfoDTO recipeInfoDTO : recipeInfoDTOList) {
            PatientDTO patientDTO = recipeInfoDTO.getPatientBean();
            if (null == patientDTO) {
                continue;
            }
            String idCard = patientDTO.getIdcard();
            if (StringUtils.isEmpty(idCard)) {
                continue;
            }
            List<Recipedetail> recipeDetails = recipeInfoDTO.getRecipeDetails();
            List<com.ngari.patient.dto.PatientDTO> patientDTOList = patientService.findByIdCard(idCard);
            for (com.ngari.patient.dto.PatientDTO patient : patientDTOList) {
                for (Recipedetail recipedetail : recipeDetails) {
                    MedicineRemindTO medicineRemindTO = new MedicineRemindTO();
                    medicineRemindTO.setCode(recipedetail.getDrugCode());
                    medicineRemindTO.setName(recipedetail.getDrugName());
                    medicineRemindTO.setBusType("recipe");
                    medicineRemindTO.setOrganId(recipeInfoDTO.getRecipe().getClinicOrgan());
                    medicineRemindTO.setMpiId(patient.getMpiId());
                    StringBuilder explan = new StringBuilder("");
                    if (StringUtils.isNotEmpty(recipedetail.getUsePathways())) {
                        explan.append(recipedetail.getUsePathways()).append(" ");
                    }
                    explan.append(recipedetail.getUseDose()).append(recipedetail.getUseDoseUnit());
                    medicineRemindTO.setExplan(explan.toString());
                    medicineRemindTO.setNum(recipedetail.getUseDays());
                    medicineRemindTO.setUnit(1);
                    medicineRemindTO.setEvery(0);
                    medicineRemindTO.setDayTime(getDayTime(recipedetail.getUsingRate()));
                    medicineRemindTOList.add(medicineRemindTO);
                }
            }
        }
        logger.info("PatientClient remindPatientTakeMedicine medicineRemindTOList:{}.", JSON.toJSONString(medicineRemindTOList));
        medicineRemindService.createMedicineRemind(medicineRemindTOList);
        return true;
    }

    /**
     * 获取每天提醒时间点
     * @param useRate 用药频率
     * @return 提醒时间点
     */
    private String getDayTime(String useRate){
        String result;
        switch (useRate) {
            case "bid":
                result = "[8,18]";
                break;
            case "tid":
                result = "[8,12,18]";
                break;
            case "qn":
                result = "[18]";
                break;
            default:
                result = "[8]";
                break;
        }
        return result;
    }
}
