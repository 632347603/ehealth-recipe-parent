package recipe.dao;

import com.ngari.recipe.drug.model.DecoctionWayBean;
import com.ngari.recipe.entity.DecoctionWay;
import com.ngari.recipe.entity.DrugList;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.annotation.RpcSupportDAO;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.Query;
import org.hibernate.StatelessSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ObjectUtils;

import java.util.List;

/**
 * @company: ngarihealth
 * @author: gaomw
 * @date:2020/8/5.
 */
@RpcSupportDAO
public abstract class DrugDecoctionWayDao extends HibernateSupportDelegateDAO<DecoctionWay> {
    public static final Logger log = LoggerFactory.getLogger(DecoctionWay.class);
    public DrugDecoctionWayDao() {
        super();
        this.setEntityName(DecoctionWay.class.getName());
        this.setKeyField("decoctionId");
    }


    @DAOMethod(sql = "from DecoctionWay where organId =:organId order by sort", limit = 0)
    public abstract List<DecoctionWayBean> findAllDecoctionWayByOrganId(@DAOParam("organId")Integer organId);

    @DAOMethod(sql = "delete from DecoctionWay where decoctionId =:decoctionId ")
    public abstract void deleteDecoctionWayByDecoctionId(@DAOParam("decoctionId")Integer decoctionId);

    public List<DecoctionWayBean> findDecoctionWayByOrganIdAndName(Integer organId, String decoctionText, Integer start, Integer limit) {
        HibernateStatelessResultAction<List<DecoctionWayBean>> action = new AbstractHibernateStatelessResultAction<List<DecoctionWayBean>>() {

            @Override public void execute(StatelessSession ss) throws DAOException {
                StringBuilder hql = new StringBuilder("from DecoctionWay where 1=1");
                if (organId != null) {
                    hql.append(" and organId =:organId");
                }
                if (!StringUtils.isEmpty(decoctionText)) {
                    hql.append(" and decoctionText like :decoctionText");
                }
                hql.append(" order by sort");
                Query query = ss.createQuery(hql.toString());
                if (organId != null) {
                    query.setParameter("organId", organId);
                }
                if (!StringUtils.isEmpty(decoctionText)) {
                    query.setParameter("decoctionText", "%" + decoctionText + "%");
                }
                query.setFirstResult(start);
                query.setMaxResults(limit);
                List<DecoctionWayBean> lists = query.list();
                setResult(lists);
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        return action.getResult();
    }
}
