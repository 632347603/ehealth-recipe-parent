package recipe.thread;

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

/**
 * 为 处方 pdf 盖章
 *
 * @author fuzi
 */
public class GenerateSignetRecipePdfRunable implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(GenerateSignetRecipePdfRunable.class);

    /**
     * 处方id
     */
    private Integer recipeId;
    /**
     * 机构id
     */
    private Integer organId;

    public GenerateSignetRecipePdfRunable(Integer recipeId, Integer organId) {
        this.recipeId = recipeId;
        this.organId = organId;
    }

    @Override
    public void run() {
        logger.info("GenerateSignetRecipePdfRunable start recipeId={}, organId={}", recipeId, organId);
        //获取配置--机构印章
        IConfigurationCenterUtilsService configurationCenterUtilsService = ApplicationUtils.getBaseService(IConfigurationCenterUtilsService.class);
        Object organSealId = configurationCenterUtilsService.getConfiguration(organId, "organSeal");
        if (null == organSealId || StringUtils.isEmpty(organSealId.toString())) {
            return;
        }
        //获取 处方数据
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        Recipe recipe = recipeDAO.get(recipeId);
        if (null == recipe || StringUtils.isEmpty(recipe.getChemistSignFile())) {
            return;
        }
        try {
            //更新pdf
            String newPfd = CreateRecipePdfUtil.generateSignetRecipePdf(recipe.getChemistSignFile(), organSealId.toString(), recipe.getRecipeType());
            if (StringUtils.isNotEmpty(newPfd)) {
                recipeDAO.updateRecipeInfoByRecipeId(recipeId, ImmutableMap.of("ChemistSignFile", newPfd));
            }
            logger.error("GenerateSignetRecipePdfRunable end newPfd={}", newPfd);
        } catch (Exception e) {
            logger.error("GenerateSignetRecipePdfRunable start recipeId={}, e={}", recipeId, e);
        }
    }
}
