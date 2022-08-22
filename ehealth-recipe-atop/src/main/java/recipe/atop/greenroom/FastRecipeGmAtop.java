package recipe.atop.greenroom;

import com.google.common.collect.Lists;
import com.ngari.recipe.dto.FastRecipeReq;
import com.ngari.recipe.entity.FastRecipe;
import com.ngari.recipe.entity.FastRecipeDetail;
import com.ngari.recipe.recipe.model.RecipeBean;
import com.ngari.recipe.vo.FastRecipeDetailVO;
import com.ngari.recipe.vo.FastRecipeVO;
import ctd.util.BeanUtils;
import ctd.util.annotation.RpcBean;
import ctd.util.annotation.RpcService;
import eh.utils.BeanCopyUtils;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import recipe.atop.BaseAtop;
import recipe.core.api.IFastRecipeBusinessService;
import recipe.vo.doctor.RecipeInfoVO;

import java.util.List;


/**
 * @Description
 * @Author yzl
 * @Date 2022-08-16
 */
@RpcBean(value = "fastRecipeGmAtop")
public class FastRecipeGmAtop extends BaseAtop {

    @Autowired
    IFastRecipeBusinessService fastRecipeService;

    /**
     * 快捷购药 开处方
     *
     * @param recipeInfoVOList
     * @return
     */
    @RpcService
    public List<Integer> fastRecipeSaveRecipeList(List<RecipeInfoVO> recipeInfoVOList) {
        for (RecipeInfoVO fastRecipeVO : recipeInfoVOList) {
            validateAtop(fastRecipeVO, fastRecipeVO.getRecipeBean());
            validateAtop("请添加项目信息！", fastRecipeVO.getRecipeDetails());
            validateAtop("请完善药方购买数量！", fastRecipeVO.getBuyNum());
            RecipeBean recipeBean = fastRecipeVO.getRecipeBean();
            validateAtop(recipeBean.getDoctor(), recipeBean.getMpiid(), recipeBean.getClinicOrgan(), recipeBean.getClinicId(), recipeBean.getDepart());
        }
        return fastRecipeService.fastRecipeSaveRecipe(recipeInfoVOList);
    }

    /**
     * 快捷购药 运营平台查询药方列表
     *
     * @param fastRecipeReq
     * @return
     */
    @RpcService
    public List<FastRecipeVO> findFastRecipeListByOrganId(FastRecipeReq fastRecipeReq) {
        validateAtop(fastRecipeReq, fastRecipeReq.getOrganId());
        isAuthorisedOrgan(fastRecipeReq.getOrganId());
        fastRecipeReq.setStatusList(Lists.newArrayList(1, 2));
        List<FastRecipe> fastRecipeList = fastRecipeService.findFastRecipeListByParam(fastRecipeReq);
        return CollectionUtils.isEmpty(fastRecipeList) ? Lists.newArrayList() : BeanCopyUtils.copyList(fastRecipeList, FastRecipeVO::new);
    }

    /**
     * 快捷购药 运营平台查询药方详情
     *
     * @param fastRecipeReq
     * @return
     */
    @RpcService
    public FastRecipeVO getFastRecipeByFastRecipeId(FastRecipeReq fastRecipeReq) {
        validateAtop(fastRecipeReq, fastRecipeReq.getFastRecipeId());
        fastRecipeReq.setStatusList(Lists.newArrayList(1, 2));
        List<FastRecipe> fastRecipeList = fastRecipeService.findFastRecipeListByParam(fastRecipeReq);
        if (CollectionUtils.isNotEmpty(fastRecipeList)) {
            FastRecipeVO fastRecipeVO = BeanUtils.map(fastRecipeList.get(0), FastRecipeVO.class);
            isAuthorisedOrgan(fastRecipeVO.getClinicOrgan());
            List<FastRecipeDetail> fastRecipeDetailList = fastRecipeService.findFastRecipeDetailsByFastRecipeId(fastRecipeList.get(0).getId());
            fastRecipeVO.setFastRecipeDetailList(BeanCopyUtils.copyList(fastRecipeDetailList, FastRecipeDetailVO::new));
            return fastRecipeVO;
        } else {
            return null;
        }
    }

    /**
     * 便捷购药 运营平台新增药方
     *
     * @param recipeId
     * @param title
     * @return
     */
    @RpcService
    public Integer addFastRecipe(Integer recipeId, String title) {
        validateAtop(recipeId, title);
        return fastRecipeService.addFastRecipe(recipeId, title);
    }

    /**
     * 运营平台药方列表页更新
     *
     * @param fastRecipeVO
     * @return
     */
    @RpcService
    public Boolean simpleUpdateFastRecipe(FastRecipeVO fastRecipeVO) {
        validateAtop(fastRecipeVO, fastRecipeVO.getId());
        return fastRecipeService.simpleUpdateFastRecipe(fastRecipeVO);
    }

    /**
     * 运营平台药方详情页更新
     *
     * @param fastRecipeVO
     * @return
     */
    @RpcService
    public Boolean fullUpdateFastRecipe(FastRecipeVO fastRecipeVO) {
        validateAtop(fastRecipeVO, fastRecipeVO.getId());
        return fastRecipeService.fullUpdateFastRecipe(fastRecipeVO);
    }

    /**
     * 患者端查询药方列表
     *
     * @param fastRecipeReq
     * @return
     */
    @RpcService
    public List<FastRecipeVO> patientfindFastRecipeListByOrganId(FastRecipeReq fastRecipeReq) {
        validateAtop(fastRecipeReq);
        fastRecipeReq.setStatusList(Lists.newArrayList(1));
        List<FastRecipe> fastRecipeList = fastRecipeService.findFastRecipeListByParam(fastRecipeReq);
        if (CollectionUtils.isNotEmpty(fastRecipeList)) {
            List<FastRecipeVO> fastRecipeVOList = Lists.newArrayList();
            for (FastRecipe fastRecipe : fastRecipeList) {
                FastRecipeVO fastRecipeVO = BeanUtils.map(fastRecipe, FastRecipeVO.class);
                List<FastRecipeDetail> fastRecipeDetailList = fastRecipeService.findFastRecipeDetailsByFastRecipeId(fastRecipeList.get(0).getId());
                fastRecipeVO.setFastRecipeDetailList(BeanCopyUtils.copyList(fastRecipeDetailList, FastRecipeDetailVO::new));
                fastRecipeVOList.add(fastRecipeVO);
            }
            return fastRecipeVOList;
        } else {
            return null;
        }
    }

    /**
     * 患者端查询药方详情
     *
     * @param ids
     * @return
     */
    @RpcService
    public List<FastRecipeVO> findFastRecipeListByIds(List<Integer> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return Lists.newArrayList();
        }

        List<FastRecipeVO> fastRecipeVOList = Lists.newArrayList();
        for (Integer id : ids) {
            FastRecipeReq fastRecipeReq = new FastRecipeReq();
            fastRecipeReq.setFastRecipeId(id);
            List<FastRecipe> fastRecipeList = fastRecipeService.findFastRecipeListByParam(fastRecipeReq);
            if (CollectionUtils.isEmpty(fastRecipeList)) {
                continue;
            }
            FastRecipeVO fastRecipeVO = BeanUtils.map(fastRecipeList.get(0), FastRecipeVO.class);
            List<FastRecipeDetail> fastRecipeDetailList = fastRecipeService.findFastRecipeDetailsByFastRecipeId(fastRecipeVO.getId());
            if (CollectionUtils.isNotEmpty(fastRecipeDetailList)) {
                fastRecipeVO.setFastRecipeDetailList(BeanCopyUtils.copyList(fastRecipeDetailList, FastRecipeDetailVO::new));
            }
            fastRecipeVOList.add(fastRecipeVO);
        }
        return fastRecipeVOList;
    }
}
