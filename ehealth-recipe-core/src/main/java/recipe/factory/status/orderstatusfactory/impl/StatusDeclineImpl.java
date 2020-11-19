package recipe.factory.status.orderstatusfactory.impl;

import com.ngari.recipe.entity.Recipe;
import com.ngari.recipe.entity.RecipeOrder;
import com.ngari.recipe.vo.UpdateOrderStatusVO;
import org.springframework.stereotype.Service;
import recipe.factory.status.constant.RecipeOrderStatusEnum;
import recipe.factory.status.constant.RecipeStatusEnum;

/**
 * 已拒发
 *
 * @author fuzi
 */
@Service
public class StatusDeclineImpl extends AbstractRecipeOrderStatus {
    @Override
    public Integer getStatus() {
        return RecipeOrderStatusEnum.ORDER_STATUS_DECLINE.getType();
    }

    @Override
    public Recipe updateStatus(UpdateOrderStatusVO orderStatus, RecipeOrder recipeOrder) {
        Recipe recipe = new Recipe();
        recipe.setRecipeId(orderStatus.getRecipeId());
        recipe.setStatus(RecipeStatusEnum.RECIPE_STATUS_DECLINE.getType());
        return recipe;
    }
}
