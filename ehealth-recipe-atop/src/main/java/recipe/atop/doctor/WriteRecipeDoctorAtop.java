package recipe.atop.doctor;

import com.ngari.patient.utils.ObjectCopyUtils;
import com.ngari.recipe.dto.OutPatientRecordResDTO;
import com.ngari.recipe.dto.RecipeDTO;
import com.ngari.recipe.dto.WriteDrugRecipeDTO;
import com.ngari.recipe.entity.Recipe;
import com.ngari.recipe.recipe.model.RecipeBean;
import ctd.util.annotation.RpcBean;
import ctd.util.annotation.RpcService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import recipe.atop.BaseAtop;
import recipe.core.api.IRecipeBusinessService;
import recipe.core.api.IRevisitBusinessService;
import recipe.enumerate.status.RecipeStateEnum;
import recipe.util.ValidateUtil;
import recipe.vo.doctor.ValidateDetailVO;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 开处方服务入口类
 *
 * @author fuzi
 */
@RpcBean("writeRecipeDoctorAtop")
public class WriteRecipeDoctorAtop extends BaseAtop {

    @Autowired
    private IRevisitBusinessService iRevisitBusinessService;
    @Autowired
    private IRecipeBusinessService recipeBusinessService;

    /**
     * 获取院内门诊
     *
     * @param mpiId    患者唯一标识
     * @param organId  机构ID
     * @param doctorId 医生ID
     * @return 院内门诊处方列表
     */
    @RpcService
    public List<WriteDrugRecipeDTO> findWriteDrugRecipeByRevisitFromHis(String mpiId, Integer organId, Integer doctorId) {
        return iRevisitBusinessService.findWriteDrugRecipeByRevisitFromHis(mpiId, organId, doctorId);
    }

    /**
     * 医生二次确认药师审核结果-不通过
     *
     * @param recipeId 处方id
     * @return
     */
    @RpcService
    public Boolean confirmAgain(Integer recipeId) {
        return recipeBusinessService.confirmAgain(recipeId);
    }

    /**
     * 获取有效门诊记录
     *
     * @param mpiId    患者唯一标识
     * @param organId  机构ID
     * @param doctorId 医生ID
     * @return 门诊记录
     */
    @RpcService
    public OutPatientRecordResDTO findOutPatientRecordFromHis(String mpiId, Integer organId, Integer doctorId) {
        return recipeBusinessService.findOutPatientRecordFromHis(mpiId, organId, doctorId);
    }

    /**
     * 靶向药拆方，无靶向药 返回空
     *
     * @param validateDetailVO 处方信息
     * @return 处方组号
     */
    @RpcService
    public String splitDrugRecipe(ValidateDetailVO validateDetailVO) {
        validateAtop(validateDetailVO, validateDetailVO.getRecipeBean(), validateDetailVO.getRecipeDetails());
        RecipeBean recipeBean = validateDetailVO.getRecipeBean();
        if (StringUtils.isEmpty(recipeBean.getGroupCode())) {
            String uuid = UUID.randomUUID().toString();
            recipeBean.setGroupCode(uuid);
        }
        recipeBean.setRecipeExtend(validateDetailVO.getRecipeExtendBean());
        recipeBean.setTargetedDrugType(1);
        return recipeBusinessService.splitDrugRecipe(recipeBean, validateDetailVO.getRecipeDetails());
    }

    /**
     * 查询同组处方
     *
     * @param groupCode 处方组号
     * @param type      0： 默认全部 1：查询暂存，2查询可撤销处方
     * @return 处方id集合
     */
    @RpcService
    public List<Integer> recipeByGroupCode(String groupCode, Integer type) {
        validateAtop(groupCode);
        return recipeBusinessService.recipeByGroupCode(groupCode, type);
    }

    /**
     * 获取处方状态 与 同组处方id
     *
     * @param recipeId recipe
     * @param type     0： 默认全部 1：查询暂存，2查询可撤销处方
     */
    @RpcService
    public RecipeBean recipeInfo(Integer recipeId, Integer type) {
        validateAtop(recipeId);
        Recipe recipe = recipeBusinessService.getByRecipeId(recipeId);
        RecipeBean recipeBean = ObjectCopyUtils.convert(recipe, RecipeBean.class);
        recipeBean.setSubStateText(RecipeStateEnum.getRecipeStateEnum(recipe.getSubState()).getName());
        if (ValidateUtil.validateObjects(recipeBean.getGroupCode())) {
            return recipeBean;
        }
        List<Integer> recipeIdList = recipeBusinessService.recipeByGroupCode(recipeBean.getGroupCode(), type);
        List<Integer> recipeIds = recipeIdList.stream().filter(a -> !a.equals(recipeBean.getRecipeId())).collect(Collectors.toList());
        recipeBean.setGroupRecipeIdList(recipeIds);
        return recipeBean;
    }


    /**
     * 获取某处方单关联处方（同一个患者同一次就诊）
     *
     * @param recipeId
     * @param doctorId
     * @return
     */
    @RpcService
    public List<RecipeDTO> findRelatedRecipeRecordByRegisterNo(Integer recipeId, Integer doctorId) {
        return recipeBusinessService.findRelatedRecipeRecordByRegisterNo(recipeId, doctorId);
    }
}
