package recipe.dao;

import com.ngari.his.recipe.mode.TakeMedicineByToHos;
import com.ngari.recipe.entity.OrganDrugsSaleConfig;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.util.annotation.RpcSupportDAO;
import recipe.dao.comment.ExtendDao;

import java.util.List;


/**
 * @description： 药企机构销售配置DAO
 * @author： whf
 * @date： 2022-01-10 15:45
 */
@RpcSupportDAO
public abstract class OrganDrugsSaleConfigDAO extends HibernateSupportDelegateDAO<OrganDrugsSaleConfig> implements ExtendDao<OrganDrugsSaleConfig> {
    @Override
    public boolean updateNonNullFieldByPrimaryKey(OrganDrugsSaleConfig config) {
        return updateNonNullFieldByPrimaryKey(config, SQL_KEY_ID);
    }

     @DAOMethod(sql = "from OrganDrugsSaleConfig where drugsEnterpriseId=:drugsEnterpriseId")
    public abstract OrganDrugsSaleConfig getOrganDrugsSaleConfig(@DAOParam("drugsEnterpriseId")Integer drugsEnterpriseId);

    @DAOMethod(sql = "from OrganDrugsSaleConfig where drugsEnterpriseId in (:saleDepIds)", limit = 0)
    public abstract List<OrganDrugsSaleConfig> findSaleConfigs(@DAOParam("saleDepIds")List<Integer> saleDepIds);

    @DAOMethod(sql = "from OrganDrugsSaleConfig where organId=:organId and drugsEnterpriseId=:drugsEnterpriseId")
    public abstract OrganDrugsSaleConfig getByOrganIdAndDrugsEnterpriseId(@DAOParam("organId")Integer organId, @DAOParam("drugsEnterpriseId")Integer drugsEnterpriseId);
}
