package recipe.dao;

import com.ngari.recipe.entity.HisRecipe;
import com.ngari.recipe.entity.Recipe;
import com.ngari.recipe.recipe.model.HisRecipeVO;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.annotation.RpcSupportDAO;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.util.List;

/**
 * @author yinsheng
 * @date 2020\3\10 0010 20:23
 */
@RpcSupportDAO
public abstract class HisRecipeDAO extends HibernateSupportDelegateDAO<HisRecipe> {

    public HisRecipeDAO() {
        super();
        this.setEntityName(HisRecipe.class.getName());
        this.setKeyField("hisRecipeID");
    }

    @DAOMethod(sql = " From HisRecipe where clinicOrgan=:clinicOrgan and mpiId=:mpiId and status=:status order by createDate desc")
    public abstract List<HisRecipe> findHisRecipes(@DAOParam("clinicOrgan") int clinicOrgan, @DAOParam("mpiId") String mpiId, @DAOParam("status") int status, @DAOParam(pageStart = true) int start,
                                                   @DAOParam(pageLimit = true) int limit);

    @DAOMethod(sql = " update HisRecipe set status=:status where clinicOrgan=:clinicOrgan and recipeCode=:recipeCode ")
    public abstract void updateHisRecieStatus(@DAOParam("clinicOrgan") int clinicOrgan, @DAOParam("recipeCode") String recipeCode, @DAOParam("status") int status);

    @DAOMethod(sql = " From HisRecipe where clinicOrgan=:clinicOrgan and recipeCode=:recipeCode")
    public abstract HisRecipe getHisRecipeByRecipeCodeAndClinicOrgan(@DAOParam("clinicOrgan") int clinicOrgan, @DAOParam("recipeCode") String recipeCode);

    /**
     * 根据 机构编号 和 处方单号 批量查询数据
     *
     * @param clinicOrgan    机构编号
     * @param recipeCodeList 处方单号
     * @return
     */
    @DAOMethod(sql = " From HisRecipe where clinicOrgan=:clinicOrgan and recipeCode in (:recipeCodeList)")
    public abstract List<HisRecipe> findHisRecipeByRecipeCodeAndClinicOrgan(@DAOParam("clinicOrgan") int clinicOrgan, @DAOParam("recipeCodeList") List<String> recipeCodeList);

    /**
     * 查询
     * @param
     * @return
     */
    public List<Integer> findHisRecipeByPayFlag(@DAOParam("recipeCodeList") List<String> recipeCodeList,
                                                @DAOParam("clinicOrgan") Integer clinicOrgan, @DAOParam("mpiid") String mpiid) {
        HibernateStatelessResultAction<List<Integer>> action = new AbstractHibernateStatelessResultAction<List<Integer>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder();
                hql.append("select r.hisRecipeID from cdr_his_recipe r where  not exists " +
                        "(select o.recipeCode,o.clinicOrgan,o.mpiid from cdr_recipe o where   r.recipeCode=o.recipeCode and r.clinicOrgan=o.clinicOrgan and r.mpiid=o.mpiid and o.payFlag=1) ");
                hql.append(" and r.recipeCode in (:recipeCodeList) and r.clinicOrgan=:clinicOrgan and r.mpiid=:mpiid ");
                Query q = ss.createSQLQuery(hql.toString());
                q.setParameterList("recipeCodeList", recipeCodeList);
                q.setParameter("clinicOrgan",clinicOrgan);
                q.setParameter("mpiid", mpiid);
                setResult(q.list());
            }
        };

        HibernateSessionTemplate.instance().execute(action);
        return action.getResult();
    }
    @DAOMethod(sql = "from Recipe where recipeCode in (:recipeCodeList) and clinicOrgan=:clinicOrgan and mpiid=:mpiid and payFlag!=1")
    public abstract List<Recipe> findByRecipeCodeAndClinicOrganAndMpiid(@DAOParam("recipeCodeList") List<String> recipeCodeList,
                                                                        @DAOParam("clinicOrgan") Integer clinicOrgan, @DAOParam("mpiid") String mpiid);


    @DAOMethod(sql = " From HisRecipe where mpiId=:mpiId and clinicOrgan=:clinicOrgan and recipeCode=:recipeCode")
    public abstract HisRecipe getHisRecipeBMpiIdyRecipeCodeAndClinicOrgan(@DAOParam("mpiId") String mpiId, @DAOParam("clinicOrgan") int clinicOrgan, @DAOParam("recipeCode") String recipeCode);

    @DAOMethod(sql = " From HisRecipe where mpiId=:mpiId and clinicOrgan=:clinicOrgan and recipeCode=:recipeCode")
    public abstract HisRecipe getHisRecipeRecipeCodeAndClinicOrgan( @DAOParam("clinicOrgan") int clinicOrgan, @DAOParam("recipeCode") String recipeCode);

    /**
     * 根据处方id批量删除
     *
     * @param hisRecipeIds
     */
    @DAOMethod(sql = "delete from HisRecipe where hisRecipeId in (:hisRecipeIds)")
    public abstract void deleteByHisRecipeIds(@DAOParam("hisRecipeIds") List<Integer> hisRecipeIds);

    @DAOMethod(sql = " From HisRecipe where hisRecipeId in (:hisRecipeIds)")
    public abstract List<HisRecipe> findHisRecipeByhisRecipeIds(@DAOParam("hisRecipeIds") List<Integer> hisRecipeIds);

}
