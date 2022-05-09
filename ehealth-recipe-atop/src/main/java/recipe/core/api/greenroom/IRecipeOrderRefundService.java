package recipe.core.api.greenroom;

import recipe.vo.greenroom.*;

/**
 * 退费查询接口调用
 *
 * @author ys
 */
public interface IRecipeOrderRefundService {

    RecipeOrderRefundPageVO findRefundRecipeOrder(RecipeOrderRefundReqVO recipeOrderRefundReqVO);

    RecipeOrderRefundDetailVO getRefundOrderDetail(String orderCode, Integer busType);

    void forceRefund(AuditRefundVO auditRefundVO);
}
