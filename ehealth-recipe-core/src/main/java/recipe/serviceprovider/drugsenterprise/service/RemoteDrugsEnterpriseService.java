package recipe.serviceprovider.drugsenterprise.service;

import com.ngari.patient.utils.ObjectCopyUtils;
import com.ngari.recipe.drugsenterprise.model.DrugsEnterpriseBean;
import com.ngari.recipe.drugsenterprise.service.IDrugsEnterpriseService;
import com.ngari.recipe.entity.DrugsEnterprise;
import com.ngari.recipe.entity.Recipe;
import com.ngari.recipe.recipe.model.RecipeBean;
import ctd.persistence.DAOFactory;
import ctd.util.annotation.RpcBean;
import org.springframework.beans.factory.annotation.Autowired;
import recipe.dao.DrugsEnterpriseDAO;
import recipe.manager.EnterpriseManager;
import recipe.serviceprovider.BaseService;

/**
 * company: ngarihealth
 *
 * @author: 0184/yu_yun
 * @date:2017/9/11.
 */
@RpcBean("remoteDrugsEnterpriseService")
public class RemoteDrugsEnterpriseService extends BaseService<DrugsEnterpriseBean> implements IDrugsEnterpriseService {
    @Autowired
    private EnterpriseManager enterpriseManager;

    @Override
    public DrugsEnterpriseBean get(Object id) {
        DrugsEnterpriseDAO enterpriseDAO = DAOFactory.getDAO(DrugsEnterpriseDAO.class);
        DrugsEnterprise enterprise = enterpriseDAO.get(id);
        return getBean(enterprise, DrugsEnterpriseBean.class);
    }

    @Override
    public void pushRecipeInfoForThird(RecipeBean recipe, DrugsEnterpriseBean drugsEnterprise) {
        enterpriseManager.pushRecipeInfoForThird(ObjectCopyUtils.convert(recipe, Recipe.class), ObjectCopyUtils.convert(drugsEnterprise, DrugsEnterprise.class), 0);
    }
}
