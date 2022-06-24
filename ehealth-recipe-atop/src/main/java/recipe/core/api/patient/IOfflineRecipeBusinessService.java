package recipe.core.api.patient;

import com.ngari.common.mode.HisResponseTO;
import com.ngari.recipe.dto.RecipeInfoDTO;
import com.ngari.recipe.offlinetoonline.model.FindHisRecipeDetailReqVO;
import com.ngari.recipe.offlinetoonline.model.FindHisRecipeDetailResVO;
import com.ngari.recipe.offlinetoonline.model.FindHisRecipeListVO;
import com.ngari.recipe.offlinetoonline.model.SettleForOfflineToOnlineVO;
import com.ngari.recipe.recipe.model.MergeRecipeVO;
import com.ngari.recipe.vo.OffLineRecipeDetailVO;
import recipe.vo.patient.RecipeGiveModeButtonRes;

import java.util.List;

/**
 * @Author liumin
 * @Date 2021/5/18 上午11:42
 * @Description 线下转线上接口类
 */
public interface IOfflineRecipeBusinessService {


    /**
     * 获取线下处方列表
     *
     * @param findHisRecipeListVO
     */
    List<MergeRecipeVO> findHisRecipeList(FindHisRecipeListVO findHisRecipeListVO);

    /**
     * 获取线下处方详情
     *
     * @param request
     * @return
     */
    FindHisRecipeDetailResVO findHisRecipeDetail(FindHisRecipeDetailReqVO request);

    /**
     * 线下处方点够药、缴费点结算 1、线下转线上 2、获取购药按钮
     *
     * @param request
     * @return
     */
    List<RecipeGiveModeButtonRes> settleForOfflineToOnline(SettleForOfflineToOnlineVO request);

    /**
     * 获取实现类 类型
     *
     * @return
     */
    String getHandlerMode();

    /**
     * 获取卡类型
     *
     * @param organId
     * @return
     */
    List<String> getCardType(Integer organId);


    /**
     * 获取线下处方详情
     *
     * @param mpiId       患者ID
     * @param clinicOrgan 机构ID
     * @param recipeCode  处方号码
     * @date 2021/8/06
     */
    OffLineRecipeDetailVO getOffLineRecipeDetails(String mpiId, Integer clinicOrgan, String recipeCode);


    /**
     * 推送处方信息到his
     *
     * @param recipeId 处方id
     * @param pushType 推送类型: 1：提交处方，2:撤销处方
     * @param sysType  处方端类型 1 医生端 2患者端
     * @return RecipeInfoDTO 处方信息
     */
    RecipeInfoDTO pushRecipe(Integer recipeId, Integer pushType, Integer sysType, Integer expressFeePayType,
                             Double expressFee, String giveModeKey);

    void offlineToOnlineForRecipe(FindHisRecipeDetailReqVO request);

    /**
     * 撤销线下处方
     *
     * @param organId
     * @param recipeCode
     * @return
     */
    HisResponseTO abolishOffLineRecipe(Integer organId, List<String> recipeCode);
}
