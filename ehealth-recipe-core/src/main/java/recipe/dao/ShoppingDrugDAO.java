package recipe.dao;

import com.ngari.recipe.entity.ShoppingDrug;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.annotation.RpcSupportDAO;
import org.hibernate.StatelessSession;

import java.util.Date;
import java.util.List;

/**
 * @author liuya
 */
@RpcSupportDAO
public class ShoppingDrugDAO extends HibernateSupportDelegateDAO<ShoppingDrug> {
    public ShoppingDrugDAO() {
        super();
        this.setEntityName(ShoppingDrug.class.getName());
        this.setKeyField("id");
    }

    /**
     * 保存订单中药品信息
     * @param drugList
     */
    public void addShoppingDrugList(final List<ShoppingDrug> drugList) {
        HibernateStatelessResultAction action = new AbstractHibernateStatelessResultAction() {
            @Override
            public void execute(StatelessSession ss) throws Exception {

                for (ShoppingDrug shoppingDrug : drugList) {
                    shoppingDrug.setCreateTime(new Date());
                    shoppingDrug.setLastModify(new Date());
                    ss.insert(shoppingDrug);
                }
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);
    }
}
