package recipe.atop;

import com.alibaba.fastjson.JSON;
import com.ngari.recipe.commonrecipe.model.CommonRecipeDTO;
import com.ngari.recipe.vo.ResultBean;
import ctd.persistence.exception.DAOException;
import ctd.util.annotation.RpcBean;
import ctd.util.annotation.RpcService;
import org.springframework.beans.factory.annotation.Autowired;
import recipe.constant.ErrorCode;
import recipe.service.CommonRecipeService;

import java.util.List;

/**
 * 处方订单服务入口类
 *
 * @author fuzi
 */
@RpcBean("commonRecipeAtop")
public class CommonRecipeAtop extends BaseAtop {

    @Autowired
    private CommonRecipeService commonRecipeService;

    /**
     * 获取常用方列表
     *
     * @param recipeType 处方类型
     * @param doctorId   医生id
     * @param organId    机构id
     * @param start      开始
     * @param limit      分页条数
     * @return ResultBean
     */
    @RpcService
    public ResultBean commonRecipeList(Integer organId, Integer doctorId, List<Integer> recipeType, int start, int limit) {
        logger.info("CommonRecipeAtop commonRecipeList organId = {},doctorId = {},recipeType = {},start = {},limit = {}"
                , organId, doctorId, recipeType, start, limit);
        if (null == doctorId && null == organId) {
            return ResultBean.serviceError("入参错误");
        }
        try {
            List<CommonRecipeDTO> result = commonRecipeService.commonRecipeList(organId, doctorId, recipeType, start, limit);
            logger.info("CommonRecipeAtop commonRecipeList result = {}", JSON.toJSONString(result));
            return ResultBean.succeed(result);
        } catch (DAOException e1) {
            logger.warn("CommonRecipeAtop commonRecipeList error", e1);
            throw new DAOException(ErrorCode.SERVICE_ERROR, e1.getMessage());
        } catch (Exception e) {
            logger.error("CommonRecipeAtop commonRecipeList error", e);
            return ResultBean.serviceError(e.getMessage(), null);
        }
    }
}
