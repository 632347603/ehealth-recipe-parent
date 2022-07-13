package recipe.core.api;

import com.ngari.recipe.dto.DoSignRecipeDTO;
import com.ngari.recipe.dto.EnterpriseStock;
import com.ngari.recipe.dto.RecipeDTO;
import com.ngari.recipe.entity.Recipe;
import com.ngari.recipe.entity.Recipedetail;
import recipe.vo.doctor.DrugEnterpriseStockVO;
import recipe.vo.doctor.DrugForGiveModeVO;
import recipe.vo.doctor.DrugQueryVO;

import java.util.List;
import java.util.Map;

/**
 * 药企处理实现类
 *
 * @author fuzi
 */
public interface IStockBusinessService {
    /**
     * 医生端查询 药品列表-查库存
     *
     * @param organId       机构id
     * @param recipeDetails 处方明细
     * @return
     */
    List<DrugEnterpriseStockVO> stockList(Integer organId, Integer recipeType, String decoctionId, List<Recipedetail> recipeDetails);

    /**
     * 医生指定药企列表-查库存
     *
     * @param recipeDTO 处方数据
     * @return
     */
    List<EnterpriseStock> stockList(RecipeDTO recipeDTO);

    /**
     * 校验 药品库存 在同一个药企下的库存数量
     * todo 兼容老代码 所以出参数用map 非特殊情况 不可使用
     *
     * @param recipeId 处方id
     * @return 是否可以开方
     */
    @Deprecated
    Map<String, Object> enterpriseStockMap(Integer recipeId);

    /**
     * 校验 某个药企下 药品库存 的库存数量
     *
     * @param recipe        机构id
     * @param recipeDetails 药品信息 drugId，code
     * @param enterpriseId  指定某药企id
     * @return 药品信息 一定存在于出参
     */
    EnterpriseStock enterpriseStockCheck(Recipe recipe, List<Recipedetail> recipeDetails, Integer enterpriseId);

    /**
     * 校验 某个药企下 药品库存 的库存数量
     *
     * @param recipeDTO    药品信息 drugId，code
     * @param enterpriseId 指定某药企id
     * @return 药品信息 一定存在于出参
     */
    EnterpriseStock enterpriseStockCheckV1(RecipeDTO recipeDTO, Integer enterpriseId);

    /**
     * 医生端 查询购药方式下有库存的药品
     *
     * @param drugQueryVO
     * @return
     */
    List<DrugForGiveModeVO> drugForGiveMode(DrugQueryVO drugQueryVO);

    /**
     * 查询药品能否开在一张处方上
     *
     * @param recipeDTO
     * @return
     */
    List<EnterpriseStock> drugRecipeStock(RecipeDTO recipeDTO);

    /**
     * 查询药品能支持的够药方式
     *
     * @param recipe
     * @return
     */
    DoSignRecipeDTO drugRecipeStockGiveMode(RecipeDTO recipe);

    /**
     * 获取药品库存
     *
     * @param recipeIds
     * @param enterpriseId
     * @return
     */
    Boolean getOrderStockFlag(List<Integer> recipeIds, Integer enterpriseId, String giveModeKey);

}
