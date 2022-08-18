package recipe.core.api;

import com.ngari.recipe.dto.FastRecipeReq;
import com.ngari.recipe.entity.FastRecipe;
import com.ngari.recipe.entity.FastRecipeDetail;
import com.ngari.recipe.vo.FastRecipeVO;
import ctd.util.annotation.RpcService;
import recipe.vo.doctor.RecipeInfoVO;
import java.util.List;

/**
 * 便捷购药
 */
public interface IFastRecipeBusinessService {
    /**
     * 便捷购药，患者端调用开处方单
     *
     * @param recipeInfoVOList
     * @return
     */
    List<Integer> fastRecipeSaveRecipe(List<RecipeInfoVO> recipeInfoVOList);


    /**
     * 便捷购药 运营平台新增药方
     *
     * @param recipeId,title
     * @return
     */
    Integer addFastRecipe(Integer recipeId, String title);

    /**
     * 查询药方列表
     *
     * @param fastRecipeReq
     * @return
     */
    List<FastRecipe> findFastRecipeListByParam(FastRecipeReq fastRecipeReq);

    /**
     * 根据药方id查询药方列表
     *
     * @param id
     * @return
     */
    @RpcService
    List<FastRecipeDetail> findFastRecipeDetailsByFastRecipeId(Integer id);

    /**
     * 运营平台药方列表页更新
     *
     * @param fastRecipeVO
     * @return
     */
    Boolean simpleUpdateFastRecipe(FastRecipeVO fastRecipeVO);

    /**
     * 运营平台药方详情页更新
     *
     * @param fastRecipeVO
     * @return
     */
    Boolean fullUpdateFastRecipe(FastRecipeVO fastRecipeVO);
}
