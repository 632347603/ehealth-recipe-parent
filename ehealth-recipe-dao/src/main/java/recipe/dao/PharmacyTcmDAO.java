package recipe.dao;

import com.google.common.collect.Maps;
import com.ngari.recipe.entity.PharmacyTcm;
import com.ngari.recipe.recipe.model.PharmacyTcmDTO;
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
import org.apache.log4j.Logger;
import org.hibernate.Query;
import org.hibernate.StatelessSession;
import recipe.constant.ErrorCode;

import java.util.List;
import java.util.Map;

/**
 * 药房dao
 *
 * @author renfuhao
 */
@RpcSupportDAO
public abstract class PharmacyTcmDAO extends HibernateSupportDelegateDAO<PharmacyTcm> {

    private static Logger logger = Logger.getLogger(PharmacyTcmDAO.class);

    public PharmacyTcmDAO() {
        super();
        this.setEntityName(PharmacyTcm.class.getName());
        this.setKeyField("pharmacyId");
    }


    /**
     * 通过orgsnId和 药房编码获取
     *
     * @param organId
     * @return
     */
    @DAOMethod(sql = "from PharmacyTcm where organId=:organId and pharmacyCode=:pharmacyCode")
    public abstract PharmacyTcm getByOrganIdAndPharmacyCode(@DAOParam("organId") Integer organId,@DAOParam("pharmacyCode") String pharmacyCode);

    /**
     * pharmacyName 查找相应药房ID
     *
     * @param pharmacyName
     * @return
     */
    @DAOMethod(sql = "select pharmacyId from PharmacyTcm where pharmacyName=:pharmacyName")
    public abstract Integer getIdByPharmacyName(@DAOParam("pharmacyName") String pharmacyName);

    /**
     * pharmacyName 查找相应药房ID
     *
     * @param pharmacyName
     * @return
     */
    @DAOMethod(sql = "select pharmacyId from PharmacyTcm where pharmacyName=:pharmacyName and organId=:organId")
    public abstract Integer getIdByPharmacyNameAndOrganId(@DAOParam("pharmacyName") String pharmacyName,@DAOParam("organId") Integer organId);

    /**
     * 通过orgsnId获取
     *
     * @param organId
     * @return
     */
    @DAOMethod(sql = "from PharmacyTcm where organId=:organId order by sort ASC " ,limit =0)
    public abstract List<PharmacyTcm> findByOrganId(@DAOParam("organId") Integer organId);


    /**
     * 通过orgsnId和 药房名称获取
     *
     * @param organId
     * @return
     */
    @DAOMethod(sql = "from PharmacyTcm where organId=:organId and pharmacyName=:pharmacyName")
    public abstract PharmacyTcm getByOrganIdAndPharmacyName(@DAOParam("organId") Integer organId,@DAOParam("pharmacyName") String pharmacyName);


    /**
     * 通过orgsnId 和症候名称  模糊查询
     * @param organId
     * @param input
     * @param start
     * @param limit
     * @return
     */
    public QueryResult<PharmacyTcmDTO> queryTempByTimeAndName(Integer organId , String input, final int start, final int limit){
        HibernateStatelessResultAction<QueryResult<PharmacyTcmDTO>> action = new AbstractHibernateStatelessResultAction<QueryResult<PharmacyTcmDTO>>(){

            @Override
            public void execute(StatelessSession ss) throws Exception {
                if (null == organId) {
                    throw new DAOException(ErrorCode.SERVICE_ERROR, "机构Id不能为空");
                }
                Map<String,Object> param = Maps.newHashMap();
                StringBuffer sql = new StringBuffer(" from PharmacyTcm where organId =:organId ");
                param.put("organId",organId);
                if (!StringUtils.isEmpty(input)){
                    sql.append(" and pharmacyName like:name ");
                    param.put("name","%"+input+"%");
                }
                Query countQuery = ss.createQuery("select count(*) "+sql.toString());
                countQuery.setProperties(param);
                Long total = (Long) countQuery.uniqueResult();

                sql.append(" order by sort ASC");
                Query query = ss.createQuery(sql.toString());
                query.setProperties(param);
                query.setFirstResult(start);
                query.setMaxResults(limit);
                List<PharmacyTcmDTO> temps = query.list();

                setResult(new QueryResult<>(total, query.getFirstResult(), query.getMaxResults(), temps));
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        return action.getResult();
    }
}
