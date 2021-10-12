package recipe.dao;

import com.ngari.patient.utils.ObjectCopyUtils;
import com.ngari.recipe.commonrecipe.model.MedicationRulesDTO;
import com.ngari.recipe.commonrecipe.model.RulesDrugCorrelationDTO;
import com.ngari.recipe.entity.DrugList;
import com.ngari.recipe.entity.RulesDrugCorrelation;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.annotation.RpcSupportDAO;
import org.apache.log4j.Logger;
import org.hibernate.Query;
import org.hibernate.StatelessSession;
import org.springframework.util.ObjectUtils;

import java.util.List;

/**
 * 合理用药规则 药品关系 表dao
 *
 * @author renfuhao
 */
@RpcSupportDAO
public abstract class RulesDrugCorrelationDAO extends HibernateSupportDelegateDAO<RulesDrugCorrelation> {
    private static Logger logger = Logger.getLogger(RulesDrugCorrelationDAO.class);

    public RulesDrugCorrelationDAO() {
        super();
        this.setEntityName(RulesDrugCorrelation.class.getName());
        this.setKeyField("id");
    }




    @DAOMethod(sql = "from RulesDrugCorrelation where medicationRulesId=:medicationRulesId  and  drugId=:drugId and  correlationDrugId=:correlationDrugId ", limit = 0)
    public abstract RulesDrugCorrelation getDrugCorrelationByCodeAndRulesId(@DAOParam("medicationRulesId") Integer medicationRulesId,@DAOParam("drugId") Integer drugId,@DAOParam("correlationDrugId") Integer correlationDrugId);


    @DAOMethod(sql = "from RulesDrugCorrelation where medicationRulesId=:medicationRulesId  and  drugId=:drugId  ", limit = 0)
    public abstract RulesDrugCorrelation getDrugCorrelationByDrugCodeAndRulesId(@DAOParam("medicationRulesId") Integer medicationRulesId,@DAOParam("drugId") Integer drugId);


    public QueryResult<RulesDrugCorrelationDTO> queryMedicationRulesBynameAndRecipeType( String input, int start, int limit) {
        HibernateStatelessResultAction<QueryResult<RulesDrugCorrelationDTO>> action = new AbstractHibernateStatelessResultAction<QueryResult<RulesDrugCorrelationDTO>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder(" from  RulesDrugCorrelation  where 1=1  ");
                if (!ObjectUtils.isEmpty(input)) {
                    hql.append(" and ( drugId like:input or drugName like:input or correlationDrugId like:input or correlationDrugName like:input   )");
                }

                hql.append("  order by createDt desc");
                Query countQuery = ss.createQuery("select count(*) " + hql.toString());
                if (!ObjectUtils.isEmpty(input)) {
                    countQuery.setParameter("input", "%" + input + "%");
                }
                Long total = (Long) countQuery.uniqueResult();

                Query q = ss.createQuery(hql.toString());
                if (!ObjectUtils.isEmpty(input)) {
                    q.setParameter("input", "%" + input + "%");
                }
                q.setFirstResult(start);
                q.setMaxResults(limit);
                List<RulesDrugCorrelation> lists = q.list();
                List<RulesDrugCorrelationDTO> convert = ObjectCopyUtils.convert(lists, RulesDrugCorrelationDTO.class);
                setResult( new QueryResult<RulesDrugCorrelationDTO>(total, q.getFirstResult(), q.getMaxResults(), convert));
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        return action.getResult();
    }
}
