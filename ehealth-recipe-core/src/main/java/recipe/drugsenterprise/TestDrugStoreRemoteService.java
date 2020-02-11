package recipe.drugsenterprise;

import com.ngari.recipe.drugsenterprise.model.DepDetailBean;
import com.ngari.recipe.drugsenterprise.model.Position;
import com.ngari.recipe.entity.DrugsEnterprise;
import com.ngari.recipe.entity.Pharmacy;
import com.ngari.recipe.entity.Recipe;
import com.ngari.recipe.hisprescription.model.HospitalRecipeDTO;
import ctd.persistence.DAOFactory;
import ctd.util.JSONUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import recipe.bean.DrugEnterpriseResult;
import recipe.constant.DrugEnterpriseConstant;
import recipe.dao.PharmacyDAO;
import recipe.dao.RecipeDAO;
import recipe.dao.RecipeParameterDao;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author yinsheng
 * @date 2019\10\16 0016 10:10
 */
public class TestDrugStoreRemoteService extends AccessDrugEnterpriseService {
    /**
     * logger
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(TestRemoteService.class);
    @Override
    public void tokenUpdateImpl(DrugsEnterprise drugsEnterprise) {
        LOGGER.info("TestDrugStoreRemoteService tokenUpdateImpl not implement.");
    }

    @Override
    public DrugEnterpriseResult pushRecipeInfo(List<Integer> recipeIds, DrugsEnterprise enterprise) {
        LOGGER.info("TestDrugStoreRemoteService pushRecipeInfo not implement.");
        return DrugEnterpriseResult.getSuccess();
    }

    @Override
    public DrugEnterpriseResult pushRecipe(HospitalRecipeDTO hospitalRecipeDTO, DrugsEnterprise enterprise) {
        return DrugEnterpriseResult.getSuccess();
    }

    @Override
    public DrugEnterpriseResult scanStock(Integer recipeId, DrugsEnterprise drugsEnterprise) {
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        Recipe recipe = recipeDAO.getByRecipeId(recipeId);
       RecipeParameterDao recipeParameterDao = DAOFactory.getDAO(RecipeParameterDao.class);
        String scanStockResult = recipeParameterDao.getByName("scanStockResult");
        if (recipe.getStatus() == -1) {
            return DrugEnterpriseResult.getSuccess();
        }
        if ("1".equals(scanStockResult)) {
            return DrugEnterpriseResult.getSuccess();
        } else {
            return DrugEnterpriseResult.getFail();
        }
    }

    @Override
    public DrugEnterpriseResult syncEnterpriseDrug(DrugsEnterprise drugsEnterprise, List<Integer> drugIdList) {
        return DrugEnterpriseResult.getSuccess();
    }

    @Override
    public DrugEnterpriseResult pushCheckResult(Integer recipeId, Integer checkFlag, DrugsEnterprise enterprise) {
        return DrugEnterpriseResult.getSuccess();
    }

    @Override
    public DrugEnterpriseResult findSupportDep(List<Integer> recipeIds, Map ext, DrugsEnterprise enterprise) {
        DrugEnterpriseResult result = DrugEnterpriseResult.getSuccess();
        RecipeParameterDao recipeParameterDao = DAOFactory.getDAO(RecipeParameterDao.class);
        String startLimit = recipeParameterDao.getByName("supportDepStartLimit");
        String[] parames = startLimit.split(",");
        Integer start = Integer.parseInt(parames[0]);
        Integer limit = Integer.parseInt(parames[1]);
        PharmacyDAO pharmacyDAO = DAOFactory.getDAO(PharmacyDAO.class);
        List<Pharmacy> pharmacies = pharmacyDAO.findAll(start, limit);
        List<DepDetailBean> depDetailBeans = new ArrayList<>();
        for (Pharmacy pharmacy : pharmacies) {
            DepDetailBean depDetailBean = new DepDetailBean();
            depDetailBean.setDepId(pharmacy.getDrugsenterpriseId());
            depDetailBean.setDepName(pharmacy.getPharmacyName());
            Position position = new Position();
            position.setLongitude(Double.parseDouble(pharmacy.getPharmacyLongitude()));
            position.setLatitude(Double.parseDouble(pharmacy.getPharmacyLatitude()));
            depDetailBean.setPosition(position);
            depDetailBean.setRecipeFee(new BigDecimal(20));
            depDetailBean.setPayMode(4);
            depDetailBean.setExpressFee(BigDecimal.ZERO);
            depDetailBean.setGysCode(pharmacy.getPharmacyCode());
            depDetailBean.setSendMethod("0");
            depDetailBean.setPayMode(20);
            depDetailBean.setAddress(pharmacy.getPharmacyAddress());
            depDetailBean.setDistance(2.0);
            depDetailBean.setPharmacyCode(pharmacy.getPharmacyCode());
            depDetailBeans.add(depDetailBean);
        }
        result.setObject(depDetailBeans);
        return result;
    }

    @Override
    public String getDrugEnterpriseCallSys() {
        return DrugEnterpriseConstant.COMPANY_TEST_DRUGSTORE;
    }
}
