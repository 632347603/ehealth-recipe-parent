package recipe.client;

import com.alibaba.fastjson.JSON;
import com.ngari.common.mode.HisResponseTO;
import com.ngari.his.recipe.mode.WriteDrugRecipeTO;
import com.ngari.his.visit.mode.WriteDrugRecipeReqTO;
import com.ngari.his.visit.service.IVisitService;
import com.ngari.recipe.entity.Recipe;
import com.ngari.recipe.entity.RecipeExtend;
import com.ngari.revisit.RevisitBean;
import com.ngari.revisit.common.model.DrugFaileToRevisitDTO;
import com.ngari.revisit.common.model.RevisitBussNoticeDTO;
import com.ngari.revisit.common.model.RevisitExDTO;
import com.ngari.revisit.common.request.ValidRevisitRequest;
import com.ngari.revisit.common.service.IRevisitBusNoticeService;
import com.ngari.revisit.common.service.IRevisitExService;
import com.ngari.revisit.common.service.IRevisitService;
import com.ngari.revisit.dto.response.RevisitBeanVO;
import com.ngari.revisit.process.service.IRecipeOnLineRevisitService;
import com.ngari.revisit.traces.service.IRevisitTracesSortService;
import ctd.util.JSONUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import recipe.util.ValidateUtil;

import java.util.List;

/**
 * 复诊相关服务
 *
 * @Author liumin
 * @Date 2021/7/22 下午2:26
 * @Description
 */
@Service
public class RevisitClient extends BaseClient {

    @Autowired
    private IRevisitExService revisitExService;

    @Autowired
    private IRevisitTracesSortService revisitTracesSortService;

    @Autowired
    private IRevisitService revisitService;

    @Autowired
    private IVisitService iVisitService;

    @Autowired
    private IRevisitBusNoticeService revisitBusNoticeService;
    @Autowired
    private IRecipeOnLineRevisitService recipeOnLineRevisitService;

    /**
     * 根据挂号序号获取复诊信息
     *
     * @param registeredId 挂号序号
     * @return 复诊信息
     */
    public RevisitExDTO getByRegisterId(String registeredId) {
        logger.info("RevisitClient getByRegisterId param registeredId:{}", registeredId);
        RevisitExDTO consultExDTO = revisitExService.getByRegisterId(registeredId);
        logger.info("RevisitClient res consultExDTO:{} ", JSONUtils.toString(consultExDTO));
        return consultExDTO;
    }

    public RevisitBean getRevisitByClinicId(Integer clinicId) {
        logger.info("RevisitClient getRevisitByClinicId param clinicId:{}", clinicId);
        RevisitBean revisitBean = revisitService.getById(clinicId);
        logger.info("RevisitClient getRevisitByClinicId param clinicId:{}", clinicId);
        return revisitBean;
    }

    /**
     * 根据复诊单号获取复诊信息
     *
     * @param clinicId 复诊单号
     * @return 复诊信息
     */
    public RevisitExDTO getByClinicId(Integer clinicId) {
        if (ValidateUtil.integerIsEmpty(clinicId)) {
            return null;
        }
        logger.info("RevisitClient getByClinicId param clinicId:{}", clinicId);
        RevisitExDTO consultExDTO = revisitExService.getByConsultId(clinicId);
        logger.info("RevisitClient getByClinicId res consultExDTO:{} ", JSONUtils.toString(consultExDTO));
        return consultExDTO;
    }

    /**
     * 通知复诊——删除处方追溯数据
     *
     * @param recipeId
     */
    public void deleteByBusIdAndBusNumOrder(Integer recipeId) {
        try {
            if (recipeId == null) {
                return;
            }
            logger.info("RevisitClient deleteByBusIdAndBusNumOrder request recipeId:{}", recipeId);
            revisitTracesSortService.deleteByBusIdAndBusNumOrder(recipeId + "", 10);
            logger.info("RevisitClient deleteByBusIdAndBusNumOrder response recipeId:{}", recipeId);
            //TODO  复诊的接口返回没有成功或失败 无法加标志 无法失败重试或批量处理失败数据
        } catch (Exception e) {
            logger.error("RevisitClient deleteByBusIdAndBusNumOrder error recipeId:{},{}", recipeId, e);
            e.printStackTrace();
        }
    }


    /**
     * 获取医生下同一个患者 最新 复诊的id
     *
     * @param mpiId        患者id
     * @param doctorId     医生id
     * @param isRegisterNo 是否存在挂号序号 获取存在挂号序号的复诊id
     * @return 复诊id
     */
    public Integer getRevisitIdByRegisterNo(String mpiId, Integer doctorId, Integer consultType, Boolean isRegisterNo) {
        ValidRevisitRequest revisitRequest = new ValidRevisitRequest();
        revisitRequest.setMpiId(mpiId);
        revisitRequest.setDoctorID(doctorId);
        revisitRequest.setRequestMode(consultType);
        revisitRequest.setRegisterNo(isRegisterNo);
        Integer revisitId = revisitService.findValidRevisitByMpiIdAndDoctorId(revisitRequest);
        logger.info("RevisitClient getRevisitId revisitRequest = {},revisitId={}", JSON.toJSONString(revisitRequest), revisitId);
        return revisitId;
    }

    /**
     * 获取复诊列表
     *
     * @param consultIds
     * @return
     */
    public List<RevisitBean> findByConsultIds(List<Integer> consultIds) {
        logger.info("RevisitClient findByConsultIds consultIds:{}.", JSONUtils.toString(consultIds));
        List<RevisitBean> revisitBeans = revisitService.findByConsultIds(consultIds);
        logger.info("RevisitClient findByConsultIds revisitBeans:{}.", JSONUtils.toString(revisitBeans));
        return revisitBeans;
    }

    /**
     * @param writeDrugRecipeReqTO 获取院内门诊请求入参
     * @return 院内门诊
     */
    public HisResponseTO<List<WriteDrugRecipeTO>> findWriteDrugRecipeByRevisitFromHis(WriteDrugRecipeReqTO writeDrugRecipeReqTO) {
        HisResponseTO<List<WriteDrugRecipeTO>> hisResponseTOList = new HisResponseTO<>();
        try {
            logger.info("RevisitClient findWriteDrugRecipeByRevisitFromHis writeDrugRecipeReqTO={}", JSONUtils.toString(writeDrugRecipeReqTO));
            hisResponseTOList = iVisitService.findWriteDrugRecipeByRevisitFromHis(writeDrugRecipeReqTO);
            logger.info("RevisitClient findWriteDrugRecipeByRevisitFromHis hisResponseTOList={}", JSON.toJSONString(hisResponseTOList));
        } catch (Exception e) {
            logger.error("RevisitClient findWriteDrugRecipeByRevisitFromHis error ", e);
        }
        return hisResponseTOList;
    }

    /**
     * 处方开成功回写复诊更改处方id
     *
     * @param recipeId
     * @param clinicId
     */
    public void updateRecipeIdByConsultId(Integer recipeId, Integer clinicId) {
        revisitExService.updateRecipeIdByConsultId(clinicId, recipeId);
    }

    public RevisitBeanVO revisitBean(Integer clinicId) {
        return revisitService.getRevisitBeanVOByConsultId(clinicId);
    }

    /**
     * 用药提醒复诊
     *
     * @param revisitBussNoticeDTO
     */
    public void remindDrugRevisit(RevisitBussNoticeDTO revisitBussNoticeDTO) {
        revisitBusNoticeService.saveSendBussNotice(revisitBussNoticeDTO);
    }

    /**
     * 发送环信消息
     *
     * @param recipe
     */
    public void sendRecipeDefeat(Recipe recipe) {
        logger.info("RevisitClient sendRecipeDefeat recipe={}", JSONUtils.toString(recipe));
        if (!Integer.valueOf(2).equals(recipe.getBussSource())) {
            return;
        }
        recipeOnLineRevisitService.sendRecipeDefeat(recipe.getRecipeId(), recipe.getClinicId());
    }

    /**
     * 便捷够药失败-给复诊法消息
     *
     * @param recipe
     */
    public void failedToPrescribeFastDrug(Recipe recipe, RecipeExtend recipeExtend) {
        DrugFaileToRevisitDTO daileToRevisitDTO = new DrugFaileToRevisitDTO();
        daileToRevisitDTO.setConsultId(recipe.getClinicId());
        daileToRevisitDTO.setRegisterNo(recipeExtend.getRegisterID());
        logger.info("RevisitClient failedToPrescribeFastDrug daileToRevisitDTO={}", JSONUtils.toString(daileToRevisitDTO));
        revisitService.failedToPrescribeFastDrug(daileToRevisitDTO);
    }
}
