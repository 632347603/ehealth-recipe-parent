package recipe.service;

import com.ngari.opbase.base.mode.BannerDTO;
import com.ngari.patient.utils.ObjectCopyUtils;
import com.ngari.recipe.commonrecipe.model.MedicationRulesDTO;
import com.ngari.recipe.commonrecipe.model.RulesDrugCorrelationDTO;
import com.ngari.recipe.drug.service.IRulesDrugCorrelationService;
import com.ngari.recipe.entity.RulesDrugCorrelation;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.util.annotation.RpcBean;
import ctd.util.annotation.RpcService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import recipe.dao.RulesDrugCorrelationDAO;

import java.util.Date;
import java.util.List;

/**
 * 合理用药规则 药品关系 服务类
 *  @author renfuhao
 */
@RpcBean("rulesDrugCorrelationService")
public class RulesDrugCorrelationService implements IRulesDrugCorrelationService {

    @Autowired
    private RulesDrugCorrelationDAO rulesDrugCorrelationDAO;


    /**
     * 合理用药规则 关联药品数据查询接口  （运营平台调用）
     * @param input
     * @return
     */
    @RpcService
    public QueryResult<RulesDrugCorrelationDTO> queryRulesDrugCorrelationByDrugCodeOrname(Integer drugId, String input,Integer rulesId, int start, int limit) {
        if (ObjectUtils.isEmpty(rulesId)){
            throw new DAOException(DAOException.VALUE_NEEDED, "规则ID is required!");
        }
        QueryResult<RulesDrugCorrelationDTO> result = rulesDrugCorrelationDAO.queryMedicationRulesBynameAndRecipeType(drugId,input,rulesId, start, limit);
        return result;
    }


    /**
     * 数据 删除
     * @param drugCorrelationId
     */
    @RpcService
    public void  deleteRulesDrugCorrelationById( Integer drugCorrelationId) {
        if (ObjectUtils.isEmpty(drugCorrelationId)){
            throw new DAOException(DAOException.VALUE_NEEDED, "drugCorrelationId is required!");
        }
        RulesDrugCorrelation rulesDrugCorrelation = rulesDrugCorrelationDAO.get(drugCorrelationId);
        if (ObjectUtils.isEmpty(rulesDrugCorrelation)){
            throw new DAOException(DAOException.VALUE_NEEDED, "未找到该规则关联药品关系数据!");
        }
        rulesDrugCorrelationDAO.remove(drugCorrelationId);
    }

    @RpcService
    public void  checkSaveRulesDrugCorrelations( List<RulesDrugCorrelationDTO>  lists,Integer rulesId) {
        if (!ObjectUtils.isEmpty(lists)){
            for (RulesDrugCorrelationDTO list : lists) {
                RulesDrugCorrelation drugCorrelation = rulesDrugCorrelationDAO.getDrugCorrelationByDrugCodeAndRulesId(rulesId, list.getDrugId());
                if (!ObjectUtils.isEmpty(drugCorrelation)){
                    throw new DAOException(DAOException.VALUE_NEEDED, "保存数据"+list.getDrugName() +"此药品已存在，请重新填写!");
                }
            }
        }
    }


    /**
     * 合理用药规则 关联药品数据新增接口
     * @param
     * @return
     */
    @RpcService
    public void addRulesDrugCorrelation( List<RulesDrugCorrelationDTO> lists,Integer rulesId) {
        if (ObjectUtils.isEmpty(rulesId)){
            throw new DAOException(DAOException.VALUE_NEEDED, "rulesId is required!");
        }
        //规则为 反 畏
        if (rulesId != 3){
            if (!ObjectUtils.isEmpty(lists)){
                for (RulesDrugCorrelationDTO list : lists) {
                    RulesDrugCorrelation convert = ObjectCopyUtils.convert(list, RulesDrugCorrelation.class);
                    RulesDrugCorrelation drugCorrelation = rulesDrugCorrelationDAO.getDrugCorrelationByCodeAndRulesId(rulesId, convert.getDrugId(), convert.getCorrelationDrugId());
                    if (!ObjectUtils.isEmpty(drugCorrelation)) {
                        throw new DAOException(DAOException.VALUE_NEEDED, "保存数据【"+list.getDrugName() +"】规则关联【"+list.getCorrelationDrugName()+"】关联关系数据已存在!");
                    }
                    //新增 反 畏  规则下 药品关系
                    convert.setCreateDt(new Date());
                    convert.setLastModify(new Date());
                    rulesDrugCorrelationDAO.save(convert);
                }
            }
        }else {
            //超量
            checkSaveRulesDrugCorrelations(lists, rulesId);
            if (!ObjectUtils.isEmpty(lists)){
                for (RulesDrugCorrelationDTO list : lists) {
                    RulesDrugCorrelation convert = ObjectCopyUtils.convert(list, RulesDrugCorrelation.class);
                    //新增 超量 规则下 药品关系
                    convert.setCreateDt(new Date());
                    convert.setLastModify(new Date());
                    rulesDrugCorrelationDAO.save(convert);
                }
            }
        }

    }

    /**
     * 反 畏 规则下前端检测 一组数据是否已存在
     * @param correlationDTO
     */
    @RpcService
    public Boolean  checkRulesDrugCorrelations( RulesDrugCorrelationDTO correlationDTO,Integer rulesId) {
        if (ObjectUtils.isEmpty(rulesId)){
            throw new DAOException(DAOException.VALUE_NEEDED, "rulesId is required!");
        }
        if (ObjectUtils.isEmpty(correlationDTO.getDrugId())){
            throw new DAOException(DAOException.VALUE_NEEDED, "drugId is required!");
        }
        if (rulesId != 3){
            RulesDrugCorrelation drugCorrelation = rulesDrugCorrelationDAO.getDrugCorrelationByCodeAndRulesId(rulesId, correlationDTO.getDrugId(), correlationDTO.getCorrelationDrugId());
            if (!ObjectUtils.isEmpty(drugCorrelation)){
                return true;
            }
        }else {
            RulesDrugCorrelation drugCorrelation = rulesDrugCorrelationDAO.getDrugCorrelationByDrugCodeAndRulesId(rulesId, correlationDTO.getDrugId());
            if (!ObjectUtils.isEmpty(drugCorrelation)){
                return true;
            }
        }
        return false;
    }


}
