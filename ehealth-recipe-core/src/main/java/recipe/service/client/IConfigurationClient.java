package recipe.service.client;

import com.alibaba.fastjson.JSON;
import com.ngari.base.property.service.IConfigurationCenterUtilsService;
import ctd.persistence.exception.DAOException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import recipe.bussutil.RecipeUtil;
import recipe.constant.ErrorCode;
import recipe.util.ByteUtils;

/**
 * 获取配置项 交互处理类
 *
 * @author fuzi
 */
@Service
public class IConfigurationClient extends BaseClient {
    @Autowired
    private IConfigurationCenterUtilsService configService;

    /**
     * 获取用药天数
     *
     * @param organId 机构id
     * @return
     */
    public String[] recipeDay(Integer organId, Integer recipeType) {
        logger.info("IConfigurationClient recipeDay organId= {},organId= {}", organId, recipeType);
        if (null == organId) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "organId is null");
        }
        String[] recipeDay;
        //中药
        if (RecipeUtil.isTcmType(recipeType)) {
            recipeDay = useDaysRange(organId);
        } else {
            //西药
            Object isCanOpenLongRecipe = configService.getConfiguration(organId, "isCanOpenLongRecipe");
            if (null != isCanOpenLongRecipe && (boolean) isCanOpenLongRecipe) {
                Object yesLongRecipe = configService.getConfiguration(organId, "yesLongRecipe");
                if (null == yesLongRecipe) {
                    throw new DAOException(ErrorCode.SERVICE_ERROR, "yesLongRecipe is null");
                }
                recipeDay = yesLongRecipe.toString().split(ByteUtils.COMMA);
            } else {
                recipeDay = useDaysRange(organId);
            }
        }

        if (2 != recipeDay.length) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "recipeDay is error");
        }
        logger.info("IConfigurationClient recipeDay recipeDay= {}", JSON.toJSONString(recipeDay));
        return recipeDay;
    }

    /**
     * 获取限制开药天数
     *
     * @param organId
     * @return
     */
    private String[] useDaysRange(Integer organId) {
        Object isLimitUseDays = configService.getConfiguration(organId, "isLimitUseDays");
        if (null != isLimitUseDays && (boolean) isLimitUseDays) {
            Object useDaysRange = configService.getConfiguration(organId, "useDaysRange");
            if (null == useDaysRange) {
                throw new DAOException(ErrorCode.SERVICE_ERROR, "useDaysRange is null");
            }
            return useDaysRange.toString().split(ByteUtils.COMMA);
        } else {
            return new String[]{"1", "99"};
        }
    }

}
