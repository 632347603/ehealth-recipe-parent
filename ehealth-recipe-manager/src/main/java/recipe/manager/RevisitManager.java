package recipe.manager;

import com.alibaba.fastjson.JSON;
import com.ngari.common.dto.RevisitTracesMsg;
import com.ngari.common.mode.HisResponseTO;
import com.ngari.his.recipe.mode.Consult;
import com.ngari.his.recipe.mode.WriteDrugRecipeTO;
import com.ngari.his.visit.mode.WriteDrugRecipeReqTO;
import com.ngari.patient.dto.AppointDepartDTO;
import com.ngari.patient.dto.HealthCardDTO;
import com.ngari.patient.dto.PatientDTO;
import com.ngari.recipe.dto.ConsultDTO;
import com.ngari.recipe.dto.WriteDrugRecipeBean;
import com.ngari.recipe.dto.WriteDrugRecipeDTO;
import com.ngari.recipe.entity.Recipe;
import com.ngari.revisit.common.model.RevisitBussNoticeDTO;
import com.ngari.revisit.common.model.RevisitExDTO;
import ctd.dictionary.DictionaryController;
import ctd.net.broadcast.MQHelper;
import ctd.util.JSONUtils;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import recipe.client.DepartClient;
import recipe.client.PatientClient;
import recipe.client.RevisitClient;
import recipe.common.OnsConfig;
import recipe.constant.RecipeSystemConstant;
import recipe.util.ObjectCopyUtils;
import recipe.util.ValidateUtil;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * 复诊处理通用类
 *
 * @author fuzi
 */
@Service
public class RevisitManager extends BaseManager {
    @Autowired
    private RevisitClient revisitClient;
    @Autowired
    private PatientClient patientClient;
    @Autowired
    private DepartClient departClient;

    /**
     * 通知复诊——添加处方追溯数据
     *
     * @param recipe
     */
    public void saveRevisitTracesList(Recipe recipe) {
        try {
            if (recipe == null) {
                logger.info("saveRevisitTracesList recipe is null ");
                return;
            }
            if (recipe.getClinicId() == null || 2 != recipe.getBussSource()) {
                logger.info("saveRevisitTracesList return param:{}", JSONUtils.toString(recipe));
                return;
            }
            RevisitTracesMsg revisitTracesMsg = new RevisitTracesMsg();
            revisitTracesMsg.setOrganId(recipe.getClinicOrgan());
            revisitTracesMsg.setConsultId(recipe.getClinicId());
            revisitTracesMsg.setBusId(recipe.getRecipeId().toString());
            revisitTracesMsg.setBusType(1);
            revisitTracesMsg.setBusNumOrder(10);
            revisitTracesMsg.setBusOccurredTime(recipe.getCreateDate());
            try {
                logger.info("saveRevisitTracesList sendMsgToMq send to MQ start, busId:{}，revisitTracesMsg:{}", recipe.getRecipeId(), JSONUtils.toString(revisitTracesMsg));
                MQHelper.getMqPublisher().publish(OnsConfig.revisitTraceTopic, revisitTracesMsg, null);
                logger.info("saveRevisitTracesList sendMsgToMq send to MQ end, busId:{}", recipe.getRecipeId());
            } catch (Exception e) {
                logger.error("saveRevisitTracesList sendMsgToMq can't send to MQ,  busId:{}", recipe.getRecipeId(), e);
            }
        } catch (Exception e) {
            logger.error("RevisitClient saveRevisitTracesList error recipeId:{}", recipe.getRecipeId(), e);
            e.printStackTrace();
        }
    }

    /**
     * 获取医生下同一个患者 最新 复诊的id
     *
     * @param mpiId        患者id
     * @param doctorId     医生id
     * @param isRegisterNo 是否存在挂号序号
     * @return 复诊id
     */
    public Integer getRevisitId(String mpiId, Integer doctorId, Boolean isRegisterNo) {
        if (isRegisterNo) {
            //获取存在挂号序号的复诊id
            return revisitClient.getRevisitIdByRegisterNo(mpiId, doctorId, RecipeSystemConstant.CONSULT_TYPE_RECIPE, true);
        }
        //获取最新的复诊id
        return revisitClient.getRevisitIdByRegisterNo(mpiId, doctorId, null, null);
    }

    /**
     * 获取院内门诊
     *
     * @param mpiId    患者唯一标识
     * @param organId  机构ID
     * @param doctorId 医生ID
     * @return 院内门诊
     */
    public List<WriteDrugRecipeDTO> findWriteDrugRecipeByRevisitFromHis(String mpiId, Integer organId, Integer doctorId) {
        com.ngari.recipe.dto.PatientDTO patient = patientClient.getPatientDTO(mpiId);
        if (null != patient) {
            WriteDrugRecipeReqTO writeDrugRecipeReqTo = getWriteDrugRecipeReqTO(patient, organId, doctorId);
            if (null != writeDrugRecipeReqTo) {
                HisResponseTO<List<WriteDrugRecipeTO>> writeDrugRecipeList = revisitClient.findWriteDrugRecipeByRevisitFromHis(writeDrugRecipeReqTo);
                return convertWriteDrugRecipeDTO(writeDrugRecipeList, patient, organId);
            }
        }
        return Collections.emptyList();
    }

    /**
     * 组装获取院内门诊请求参数
     *
     * @param patient
     * @param organId
     * @param doctorId
     * @return
     */
    public WriteDrugRecipeReqTO getWriteDrugRecipeReqTO(com.ngari.recipe.dto.PatientDTO patient, Integer organId, Integer doctorId) {
        logger.info("RevisitManager writeDrugRecipeReqTO patient={},organId={},doctorId={}", JSONUtils.toString(patient), JSONUtils.toString(organId), JSONUtils.toString(doctorId));
        List<HealthCardDTO> healthCardDTOList = new ArrayList<>();
        //出参对象
        WriteDrugRecipeReqTO writeDrugRecipeReqTo = new WriteDrugRecipeReqTO();
        try {
            healthCardDTOList = patientClient.queryCardsByParam(organId, patient.getMpiId(), new ArrayList<>(Arrays.asList("1", "2", "3", "6")));
            logger.info("queryCardsByParam res:{}", JSONUtils.toString(healthCardDTOList));
        } catch (Exception e) {
            logger.error("queryCardsByParam 获取卡号错误", e);
        }
        writeDrugRecipeReqTo.setHealthCardDTOList(healthCardDTOList);
        writeDrugRecipeReqTo.setOrganId(organId);
        writeDrugRecipeReqTo.setDoctorId(doctorId);
        writeDrugRecipeReqTo.setPatientName(patient.getPatientName());
        writeDrugRecipeReqTo.setCertificate(patient.getCertificate());
        writeDrugRecipeReqTo.setCertificateType(patient.getCertificateType());
        writeDrugRecipeReqTo.setPatientDTO(ObjectCopyUtils.convert(patient, PatientDTO.class));
        logger.info("RevisitManager writeDrugRecipeReqTO={}", JSON.toJSONString(writeDrugRecipeReqTo));
        return writeDrugRecipeReqTo;
    }

    /**
     * 组装院内门诊返回数据
     *
     * @param writeDrugRecipeList
     * @param patient
     * @param organId
     * @return
     */
    public List<WriteDrugRecipeDTO> convertWriteDrugRecipeDTO(HisResponseTO<List<WriteDrugRecipeTO>> writeDrugRecipeList, com.ngari.recipe.dto.PatientDTO patient, Integer organId) {
        com.ngari.recipe.dto.PatientDTO patientDTO = ObjectCopyUtils.convert(patient, com.ngari.recipe.dto.PatientDTO.class);
        PatientDTO requestPatient = new PatientDTO();
        requestPatient.setPatientName(patient.getPatientName());
        com.ngari.recipe.dto.PatientDTO requestPatientDTO = ObjectCopyUtils.convert(requestPatient, com.ngari.recipe.dto.PatientDTO.class);
        //组装院内门诊返回数据
        List<WriteDrugRecipeDTO> writeDrugRecipeDTOList = new ArrayList<>();
        if (null != writeDrugRecipeList) {
            List<WriteDrugRecipeTO> dataList = writeDrugRecipeList.getData();
            if (CollectionUtils.isEmpty(dataList)) {
                return writeDrugRecipeDTOList;
            }
            try {
                for (WriteDrugRecipeTO writeDrugRecipeTo : dataList) {
                    WriteDrugRecipeDTO writeDrugRecipeDTO = new WriteDrugRecipeDTO();
                    WriteDrugRecipeBean writeDrugRecipeBean = new WriteDrugRecipeBean();
                    Consult consult = writeDrugRecipeTo.getConsult();
                    if (consult == null) {
                        continue;
                    }
                    ConsultDTO consultDTO = ObjectCopyUtils.convert(consult, ConsultDTO.class);
                    String appointDepartCode = consult.getAppointDepartCode();
                    AppointDepartDTO appointDepartDTO = departClient.getAppointDepartByOrganIdAndAppointDepartCode(organId, appointDepartCode);
                    logger.info("WriteRecipeManager findWriteDrugRecipeByRevisitFromHis appointDepartDTO={}", JSONUtils.toString(appointDepartDTO));
                    if (null != appointDepartDTO) {
                        writeDrugRecipeBean.setAppointDepartInDepartId(appointDepartDTO.getDepartId());
                        String consultDepartText = DictionaryController.instance().get("eh.base.dictionary.Depart").getText(appointDepartDTO.getDepartId());
                        if (null != consultDTO) {
                            consultDTO.setConsultDepart(appointDepartDTO.getDepartId());
                            consultDTO.setConsultDepartText(consultDepartText);
                        }
                    }
                    writeDrugRecipeDTO.setAppointDepartCode(appointDepartCode);
                    writeDrugRecipeDTO.setAppointDepartName(consult.getAppointDepartName());
                    writeDrugRecipeDTO.setPatient(patientDTO);
                    writeDrugRecipeDTO.setRequestPatient(requestPatientDTO);
                    writeDrugRecipeDTO.setConsult(consultDTO);
                    writeDrugRecipeDTO.setType(writeDrugRecipeTo.getType());
                    writeDrugRecipeDTO.setWriteDrugRecipeBean(writeDrugRecipeBean);
                    logger.info("WriteRecipeManager findWriteDrugRecipeByRevisitFromHis writeDrugRecipeDTO={}", JSONUtils.toString(writeDrugRecipeDTO));
                    writeDrugRecipeDTOList.add(writeDrugRecipeDTO);
                }
            } catch (Exception e) {
                logger.error("WriteRecipeManager findWriteDrugRecipeByRevisitFromHis error={}", JSONUtils.toString(e));
            }
        }
        return writeDrugRecipeDTOList;

    }

    /**
     * 处方开成功回写复诊更改处方id
     *
     * @param recipeId
     * @param clinicId
     */
    public void updateRecipeIdByConsultId(Integer recipeId, Integer clinicId) {
        revisitClient.updateRecipeIdByConsultId(recipeId, clinicId);
    }

    /**
     * 增加复诊 医保状态获取
     * 如新增咨询等可用责任链模式
     *
     * @param clinicId
     * @param bussSource
     * @return 是否医保 0自费 1医保
     */
    public Integer medicalFlag(Integer clinicId, Integer bussSource) {
        if (ValidateUtil.validateObjects(clinicId, bussSource)) {
            return null;
        }
        if (!bussSource.equals(2)) {
            return null;
        }
        RevisitExDTO revisitExDTO = revisitClient.getByClinicId(clinicId);
        if (null == revisitExDTO) {
            return null;
        }
        return revisitExDTO.getMedicalFlag();
    }

    /**
     * 用药提醒复诊
     * @param recipe
     * @param remindDates
     */
    public void remindDrugForRevisit(Recipe recipe, List<LocalDateTime> remindDates, int pushMode){
        if (null == recipe || CollectionUtils.isEmpty(remindDates)) {
            return;
        }
        remindDates.forEach(date -> {
            RevisitBussNoticeDTO revisitBussNoticeDTO = new RevisitBussNoticeDTO();
            revisitBussNoticeDTO.setOrganId(recipe.getClinicOrgan());
            revisitBussNoticeDTO.setDeptId(recipe.getDepart());
            revisitBussNoticeDTO.setDoctorId(recipe.getDoctor());
            revisitBussNoticeDTO.setBusId(recipe.getRecipeId().toString());
            revisitBussNoticeDTO.setLastConsultId(recipe.getClinicId());
            revisitBussNoticeDTO.setMpiId(recipe.getMpiid());
            revisitBussNoticeDTO.setBusType(1);
            revisitBussNoticeDTO.setSendTime(Date.from(date.atZone(ZoneId.systemDefault()).toInstant()));
            revisitBussNoticeDTO.setRequestDate(recipe.getCreateDate());
            revisitBussNoticeDTO.setStatisticType(pushMode);
            logger.info("remindDrugForRevisit revisitBussNoticeDTO:{}", JSON.toJSONString(revisitBussNoticeDTO));
            revisitClient.remindDrugRevisit(revisitBussNoticeDTO);
        });
    }

}
