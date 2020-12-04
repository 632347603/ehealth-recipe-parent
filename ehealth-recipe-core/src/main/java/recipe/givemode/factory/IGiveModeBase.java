package recipe.givemode.factory;

import com.ngari.recipe.entity.Recipe;
import com.ngari.recipe.recipe.model.GiveModeShowButtonVO;
import com.ngari.recipe.recipe.model.PatientTabStatusRecipeDTO;

/**
 * @author yinsheng
 * @date 2020\12\3 0003 20:04
 */
public interface IGiveModeBase {

    /**
     * 校验数据项
     * @param recipe 处方单
     */
    void validRecipeData(Recipe recipe);

    /**
     * 通过机构ID从运营平台获取购药方式的基本配置项
     * @param organId 机构ID
     * @return        运营平台的配置项
     */
    GiveModeShowButtonVO getGiveModeSettingFromYt(Integer organId);

    /**
     * 设置按钮是否可操作（比如 审方方式是前置且正在审核中时，按钮是置灰的）
     * @param  giveModeShowButtonVO 按钮
     */
    void setButtonOptional(GiveModeShowButtonVO giveModeShowButtonVO, Recipe recipe);

    /**
     * 设置平台和互联网关于按钮个性化显示
     * @param record
     * @param giveModeShowButtonVO
     * @param recipe
     */
    void setSpecialItem(PatientTabStatusRecipeDTO record, GiveModeShowButtonVO giveModeShowButtonVO, Recipe recipe);

    /**
     * 设置其他按钮 如：用药指导
     * @param record
     * @param giveModeShowButtonVO
     * @param recipe
     */
    void setOtherButton(PatientTabStatusRecipeDTO record, GiveModeShowButtonVO giveModeShowButtonVO, Recipe recipe);

    /**
     * 设置按钮展示类型(0待处理，1待支付，2查看物流，3不展示按钮（平台+互联网），4已完成，展示用药指导按钮（平台+互联网）)
     * @param record
     * @param giveModeShowButtonVO
     * @param recipe
     */
    void setButtonType(PatientTabStatusRecipeDTO record, GiveModeShowButtonVO giveModeShowButtonVO, Recipe recipe);

    /**
     * 根据处方中的机构和配送类型获取文案展示
     * @param recipe 处方信息
     * @return       购药文案
     */
    String getGiveModeTextByRecipe(Recipe recipe);
}
