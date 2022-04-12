package recipe.atop.open;

import com.alibaba.fastjson.JSONArray;
import com.ngari.recipe.entity.Recipe;
import com.ngari.recipe.hisprescription.model.RegulationRecipeIndicatorsDTO;
import com.ngari.recipe.offlinetoonline.model.FindHisRecipeDetailReqVO;
import com.ngari.recipe.recipe.model.RecipeBean;
import com.ngari.recipe.vo.FormWorkRecipeReqVO;
import com.ngari.recipe.vo.FormWorkRecipeVO;
import ctd.persistence.exception.DAOException;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcBean;
import org.springframework.beans.factory.annotation.Autowired;
import recipe.api.open.IRecipeAtopService;
import recipe.atop.BaseAtop;
import recipe.constant.ErrorCode;
import recipe.core.api.IRecipeBusinessService;
import recipe.core.api.IRevisitBusinessService;
import recipe.core.api.patient.IOfflineRecipeBusinessService;
import recipe.core.api.patient.IPatientBusinessService;
import recipe.enumerate.status.RecipeAuditStateEnum;
import recipe.util.ObjectCopyUtils;
import recipe.vo.doctor.RecipeInfoVO;
import recipe.vo.patient.PatientOptionalDrugVo;
import recipe.vo.second.RevisitRecipeTraceVo;

import java.util.Date;
import java.util.List;

/**
 * 处方服务入口类
 *
 * @author zhaoh
 * @date 2021/7/19
 */
@RpcBean("recipeOpenAtop")
public class RecipeOpenAtop extends BaseAtop implements IRecipeAtopService {

    @Autowired
    private IRecipeBusinessService recipeBusinessService;

    @Autowired
    private IRevisitBusinessService revisitRecipeTrace;

    @Autowired
    IOfflineRecipeBusinessService offlineToOnlineService;

    @Autowired
    private IPatientBusinessService recipePatientService;

    @Override
    public Boolean existUncheckRecipe(Integer bussSource, Integer clinicId) {
        logger.info("existUncheckRecipe bussSource={} clinicID={}", bussSource, clinicId);
        validateAtop(bussSource, clinicId);
        try {
            //接口结果 True存在 False不存在
            Boolean result = recipeBusinessService.existUncheckRecipe(bussSource, clinicId);
            logger.info("RecipeOpenAtop existUncheckRecipe result = {}", result);
            return result;
        } catch (DAOException e1) {
            logger.error("RecipeOpenAtop existUncheckRecipe error", e1);
            throw new DAOException(ErrorCode.SERVICE_ERROR, e1.getMessage());
        } catch (Exception e) {
            logger.error("RecipeOpenAtop existUncheckRecipe error e", e);
            throw new DAOException(ErrorCode.SERVICE_ERROR, e.getMessage());
        }
    }

    /**
     * 复诊处方追溯
     *
     * @param recipeId 处方ID
     * @param clinicId 复诊ID
     * @return
     */
    @Override
    public List<RevisitRecipeTraceVo> revisitRecipeTrace(Integer recipeId, Integer clinicId) {
        logger.info("RecipeOpenAtop revisitRecipeTrace bussSource={} clinicID={}", recipeId, clinicId);
        if (clinicId == null && recipeId == null) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "请传入业务ID或复诊ID");
        }
        try {
            List<RevisitRecipeTraceVo> result = revisitRecipeTrace.revisitRecipeTrace(recipeId, clinicId);
            logger.info("RecipeOpenAtop existUncheckRecipe result = {}", result);
            return result;
        } catch (DAOException e1) {
            logger.error("RecipeOpenAtop existUncheckRecipe error", e1);
            throw new DAOException(e1.getCode(), e1.getMessage());
        } catch (Exception e) {
            logger.error("RecipeOpenAtop existUncheckRecipe error e", e);
            throw new DAOException(ErrorCode.SERVICE_ERROR, e.getMessage());
        }
    }

    /**
     * 复诊处方追溯列表数据处理
     *
     * @param startTime
     * @param endTime
     * @param recipeIds
     * @param organId
     */
    @Override
    public void handDealRevisitTraceRecipe(String startTime, String endTime, List<Integer> recipeIds, Integer organId) {
        logger.info("RecipeOpenAtop handDealRevisitTraceRecipe startTime={} endTime={} recipeIds={} organId={}", startTime, endTime, JSONUtils.toString(recipeIds), organId);
        try {
            revisitRecipeTrace.handDealRevisitTraceRecipe(startTime, endTime, recipeIds, organId);
            logger.info("RecipeOpenAtop handDealRevisitTraceRecipe end");
        } catch (DAOException e1) {
            logger.error("RecipeOpenAtop handDealRevisitTraceRecipe error", e1);
            throw new DAOException(e1.getCode(), e1.getMessage());
        } catch (Exception e) {
            logger.error("RecipeOpenAtop handDealRevisitTraceRecipe error e", e);
            throw new DAOException(ErrorCode.SERVICE_ERROR, e.getMessage());
        }
    }

    @Override
    public RecipeBean getByRecipeId(Integer recipeId) {
        logger.info("RecipeOpenAtop getByRecipeId recipeId={}", recipeId);
        validateAtop(recipeId);
        try {
            Recipe recipe = recipeBusinessService.getByRecipeId(recipeId);
            RecipeBean result = ObjectCopyUtils.convert(recipe, RecipeBean.class);
            logger.info("RecipeOpenAtop getByRecipeId  result = {}", JSONUtils.toString(result));
            return result;
        } catch (DAOException e1) {
            logger.warn("RecipeOpenAtop getByRecipeId error", e1);
            throw new DAOException(e1.getCode(), e1.getMessage());
        } catch (Exception e) {
            logger.error("RecipeOpenAtop getByRecipeId error e", e);
            throw new DAOException(ErrorCode.SERVICE_ERROR, e.getMessage());
        }
    }

    @Override
    public List<RecipeBean> findRecipesByStatusAndInvalidTime(List<Integer> status, Date invalidTime) {
        List<Recipe> recipes = recipeBusinessService.findRecipesByStatusAndInvalidTime(status, invalidTime);
        return ObjectCopyUtils.convert(recipes, RecipeBean.class);
    }

    @Override
    public void savePatientDrug(PatientOptionalDrugVo patientOptionalDrugVo) {
        logger.info("RecipeOpenAtop savePatientDrug patientOptionalDrugVo={}", JSONArray.toJSONString(patientOptionalDrugVo));
        validateAtop(patientOptionalDrugVo.getClinicId(), patientOptionalDrugVo.getDrugId(), patientOptionalDrugVo.getOrganDrugCode(), patientOptionalDrugVo.getOrganId(), patientOptionalDrugVo.getPatientDrugNum());
        recipeBusinessService.savePatientDrug(patientOptionalDrugVo);
    }

    @Override
    public RegulationRecipeIndicatorsDTO regulationRecipe(Integer recipeId) {
        validateAtop(recipeId);
        return recipeBusinessService.regulationRecipe(recipeId);
    }

    @Override
    public void offlineToOnlineForRecipe(FindHisRecipeDetailReqVO request) {
        logger.info("recipeOpenAtop findHisRecipeDetail request:{}", ctd.util.JSONUtils.toString(request));
        validateAtop(request, request.getOrganId(), request.getMpiId());
        offlineToOnlineService.offlineToOnlineForRecipe(request);
    }

    @Override
    public Boolean updateAuditState(Integer recipeId, Integer state) {
        return recipeBusinessService.updateAuditState(recipeId, RecipeAuditStateEnum.getRecipeAuditStateEnum(state));
    }

    @Override
    public RecipeBean getByRecipeCodeAndRegisterIdAndOrganId(String recipeCode, String registerId, int organId) {
        validateAtop(recipeCode, organId);
        return recipeBusinessService.getByRecipeCodeAndRegisterIdAndOrganId(recipeCode, registerId, organId);
    }

    @Override
    public FormWorkRecipeVO getFormWorkRecipeById(Integer mouldId, Integer organId) {
        FormWorkRecipeReqVO formWorkRecipeReqVO = new FormWorkRecipeReqVO();
        formWorkRecipeReqVO.setOrganId(organId);
        List<FormWorkRecipeVO> formWorkRecipeVOList = recipePatientService.findFormWorkRecipe(formWorkRecipeReqVO);
        formWorkRecipeVOList.stream().filter(a -> a.getMouldId().equals(mouldId));
        return formWorkRecipeVOList.get(0);
    }


}