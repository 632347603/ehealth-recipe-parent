package recipe.dao;

import com.ngari.recipe.entity.RecipeExtend;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcSupportDAO;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.Query;
import org.hibernate.StatelessSession;
import org.springframework.util.ObjectUtils;

import java.util.List;
import java.util.Map;

/**
 * 处方扩展表
 * Created by yuzq on 2019/3/1.
 */
@RpcSupportDAO
public abstract class RecipeExtendDAO extends HibernateSupportDelegateDAO<RecipeExtend> {

    private static final Log LOGGER = LogFactory.getLog(RecipeExtendDAO.class);

    public RecipeExtendDAO() {
        super();
        this.setEntityName(RecipeExtend.class.getName());
        this.setKeyField("recipeId");
    }

    /**
     * 根据id获取
     *
     * @param recipeId
     * @return
     */
    @DAOMethod
    public abstract RecipeExtend getByRecipeId(int recipeId);


    public void saveRecipeExtend(RecipeExtend recipeExtend) {
        LOGGER.info("处方扩展表保存：" + JSONUtils.toString(recipeExtend));
        super.save(recipeExtend);
    }

    public Boolean updateCardInfoById(final int recipeId, final String cardTypeName , final String cardNo) {
        HibernateStatelessResultAction<Boolean> action = new AbstractHibernateStatelessResultAction<Boolean>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder("update RecipeExtend set cardNo=:cardNo, cardTypeName=:cardTypeName where recipeId=:recipeId");
                Query q = ss.createQuery(hql.toString());
                q.setParameter("recipeId", recipeId);
                q.setParameter("cardTypeName", cardTypeName);
                q.setParameter("cardNo", cardNo);
                int flag = q.executeUpdate();
                setResult(flag == 1);
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        return action.getResult();
    }


    /**
     * 保存OR更新
     * @param recipeExtend
     */
    public void saveOrUpdateRecipeExtend(RecipeExtend recipeExtend) {
        if(null == recipeExtend.getRecipeId()){
            return;
        }
        if (ObjectUtils.isEmpty(this.getByRecipeId(recipeExtend.getRecipeId()))) {
            this.save(recipeExtend);
        } else {
            this.update(recipeExtend);
        }
    }

    /**
     * 更新处方自定义字段
     *
     * @param recipeId
     * @param changeAttr
     * @return
     */
    public Boolean updateRecipeExInfoByRecipeId(final int recipeId, final Map<String, ?> changeAttr) {
        if (null == changeAttr || changeAttr.isEmpty()) {
            return true;
        }

        HibernateStatelessResultAction<Boolean> action = new AbstractHibernateStatelessResultAction<Boolean>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder("update RecipeExtend set ");
                StringBuilder keyHql = new StringBuilder();
                for (String key : changeAttr.keySet()) {
                    keyHql.append("," + key + "=:" + key);
                }
                hql.append(keyHql.toString().substring(1)).append(" where recipeId=:recipeId");
                Query q = ss.createQuery(hql.toString());

                q.setParameter("recipeId", recipeId);
                for (String key : changeAttr.keySet()) {
                    q.setParameter(key, changeAttr.get(key));
                }

                int flag = q.executeUpdate();
                setResult(flag == 1);
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        return action.getResult();
    }

    /*
     * @description 根据天猫回传处方编码获取处方id集合
     * @author gmw
     * @date 2019/9/23
     * @param rxNo
     * @return List<Integer>
     */
    @DAOMethod(sql = "select recipeId from RecipeExtend where rxNo=:rxNo")
    public abstract List<Integer> findRecipeIdsByRxNo(@DAOParam("rxNo") String rxNo);


}
