package recipe.atop.doctor;

import com.ngari.recipe.dto.RecipeRefundDTO;
import ctd.util.annotation.RpcBean;
import ctd.util.annotation.RpcService;
import eh.utils.ValidateUtil;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import recipe.atop.BaseAtop;
import recipe.core.api.IRecipeBusinessService;
import recipe.core.api.patient.IRecipeOrderBusinessService;
import recipe.vo.PageGenericsVO;
import recipe.vo.greenroom.RecipeRefundInfoReqVO;

import java.util.Collections;
import java.util.List;

/**
 * 医生端-查处方服务入口类
 *
 * @author zgy
 * @date 2022/9/20
 */
@RpcBean("findRecipeDoctorAtop")
public class FindRecipeDoctorAtop extends BaseAtop {
    @Autowired
    private IRecipeBusinessService recipeBusinessService;
    @Autowired
    private IRecipeOrderBusinessService recipeOrderService;

    /**
     * 医生端-我的数据获取已退费列表
     * @param recipeRefundInfoReqVO
     */
    @RpcService
    public PageGenericsVO<RecipeRefundDTO> getRecipeRefundInfo(RecipeRefundInfoReqVO recipeRefundInfoReqVO) {
        validateAtop(recipeRefundInfoReqVO,recipeRefundInfoReqVO.getDoctorId(),recipeRefundInfoReqVO.getStartTime(),
                recipeRefundInfoReqVO.getEndTime(),recipeRefundInfoReqVO.getStart(),recipeRefundInfoReqVO.getLimit());
        PageGenericsVO<RecipeRefundDTO> result = new PageGenericsVO<>();
        result.setStart(recipeRefundInfoReqVO.getStart());
        result.setLimit(recipeRefundInfoReqVO.getLimit());
        result.setDataList(Collections.emptyList());
        Integer refundCount = recipeOrderService.getRecipeRefundCount(recipeRefundInfoReqVO);
        result.setTotal(refundCount);
        if (ValidateUtil.nullOrZeroInteger(refundCount)) {
            return result;
        }
        recipeRefundInfoReqVO.setStart((recipeRefundInfoReqVO.getStart() - 1) * recipeRefundInfoReqVO.getLimit());
        List<RecipeRefundDTO> recipeRefundInfo = recipeBusinessService.getRecipeRefundInfo(recipeRefundInfoReqVO);
        if (CollectionUtils.isEmpty(recipeRefundInfo)) {
            return result;
        }
        result.setDataList(recipeRefundInfo);
        return result;
    }

}
