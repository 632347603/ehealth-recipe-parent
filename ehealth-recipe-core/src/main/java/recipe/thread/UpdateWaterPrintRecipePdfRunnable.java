package recipe.thread;

import com.alibaba.fastjson.JSON;
import com.google.common.collect.ImmutableMap;
import com.ngari.base.property.service.IConfigurationCenterUtilsService;
import com.ngari.recipe.entity.Recipe;
import ctd.persistence.DAOFactory;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import recipe.ApplicationUtils;
import recipe.bussutil.CreateRecipePdfUtil;
import recipe.dao.RecipeDAO;
import recipe.util.DateConversion;

/**
 *  所有ca模式在医生签名完成后异步添加水印
 * @author liumin
 */

public class UpdateWaterPrintRecipePdfRunnable implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(UpdateWaterPrintRecipePdfRunnable.class);

    private Integer recipeId;

    public UpdateWaterPrintRecipePdfRunnable(Integer recipeId) {
        this.recipeId = recipeId;
    }

    @Override
    public void run() {
        long start = System.currentTimeMillis();
        logger.info("UpdateWaterPrintRecipePdfRunnable start. recipeId={}", recipeId);
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        Recipe recipe = recipeDAO.get(recipeId);
        logger.info("UpdateWaterPrintRecipePdfRunnable recipeId={} recipe={}", recipeId, JSON.toJSONString(recipe));
        //更新pdf
        if (null == recipe) {
            logger.warn("UpdateWaterPrintRecipePdfRunnable recipe is null  recipeId={}", recipeId);
            return;
        }
        try {
            String newPfd = null;
            String key = null;
            //如果不是医生签名，则不添加水印
            if(null !=recipe.getChecker()){
                return;
            }
            //如果机构配置未配置水印
            IConfigurationCenterUtilsService configService = ApplicationUtils.getBaseService(IConfigurationCenterUtilsService.class);
            Object waterPrintText = configService.getConfiguration(recipe.getClinicOrgan(), "waterPrintText");
            Boolean isShowTime = (Boolean) configService.getConfiguration(recipe.getClinicOrgan(), "waterPrintRecipeWithTime");
            if ((null == waterPrintText ||StringUtils.isEmpty(waterPrintText.toString())) && !isShowTime) {
                return;
            }
            String dateFormatter = DateConversion.getDateFormatter(recipe.getSignDate(), "yyyy/MM/dd HH:mm");
            if (StringUtils.isNotEmpty(recipe.getSignFile())) {
                newPfd = CreateRecipePdfUtil.generateWaterPrintRecipePdf(recipe.getSignFile(), (waterPrintText != null ? waterPrintText.toString() : "") + (isShowTime ? " " + dateFormatter : ""));
                key = "SignFile";
            } else {
                logger.warn("UpdateWaterPrintRecipePdfRunnable file is null  recipeId={}", recipeId);
            }
            logger.info("UpdateWaterPrintRecipePdfRunnable file recipeId={},newPfd ={},key ={}",recipeId, newPfd, key);
            if (StringUtils.isNotEmpty(newPfd) && StringUtils.isNotEmpty(key)) {
                recipeDAO.updateRecipeInfoByRecipeId(recipeId, ImmutableMap.of(key, newPfd));
            }

        } catch (Exception e) {
            logger.error("UpdateWaterPrintRecipePdfRunnable error recipeId={},e=", recipeId, e);
        }finally {
            long elapsedTime = System.currentTimeMillis() - start;
            logger.info("RecipeBusThreadPool UpdateWaterPrintRecipePdfRunnable 在sendSuccess-PDF-添加水印 执行时间:{}.", elapsedTime);
        }
    }
}
