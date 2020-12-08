package recipe.givemode.business;

import com.ngari.recipe.entity.HisRecipe;
import com.ngari.recipe.entity.Recipe;
import com.ngari.recipe.entity.RecipeExtend;
import ctd.persistence.DAOFactory;
import ctd.util.AppContextHolder;
import org.apache.commons.lang3.StringUtils;
import recipe.dao.HisRecipeDAO;
import recipe.dao.RecipeExtendDAO;

/**
 * @author yinsheng
 * @date 2020\12\3 0003 19:49
 */
public class GiveModeFactory {

    public static IGiveModeBase getGiveModeBaseByRecipeMode(Recipe recipe) {
        IGiveModeBase giveModeBase = AppContextHolder.getBean("commonGiveModeService", commonGiveModeService.class);
        if (new Integer(2).equals(recipe.getRecipeSource())) {
            //表示来源于线下转线上的处方单
            HisRecipeDAO hisRecipeDAO = DAOFactory.getDAO(HisRecipeDAO.class);
            HisRecipe hisRecipe = hisRecipeDAO.getHisRecipeByRecipeCodeAndClinicOrgan(recipe.getClinicOrgan(), recipe.getRecipeCode());
            //只有北京互联网医院DeliveryCode是不为空的
            if (hisRecipe != null && StringUtils.isNotEmpty(hisRecipe.getDeliveryCode())) {
                return AppContextHolder.getBean("bjGiveModeService", BjGiveModeService.class);
            }
        }
        RecipeExtendDAO recipeExtendDAO = DAOFactory.getDAO(RecipeExtendDAO.class);
        RecipeExtend recipeExtend = recipeExtendDAO.getByRecipeId(recipe.getRecipeId());
        //杭州市互联网用到从预校验中his返回的配送方式
        if (null != recipeExtend && null != recipeExtend.getGiveModeFormHis()) {
            return AppContextHolder.getBean("fromHisGiveModeService", BjGiveModeService.class);
        }
        return giveModeBase;
    }
}
