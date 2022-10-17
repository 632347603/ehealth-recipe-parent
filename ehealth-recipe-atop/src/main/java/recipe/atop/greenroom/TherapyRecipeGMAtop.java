package recipe.atop.greenroom;

import com.alibaba.fastjson.JSON;
import com.ngari.recipe.basic.ds.PatientVO;
import com.ngari.recipe.dto.RecipeInfoDTO;
import com.ngari.recipe.dto.RecipeTherapyOpDTO;
import com.ngari.recipe.dto.RecipeTherapyOpQueryDTO;
import com.ngari.recipe.recipe.model.*;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcBean;
import ctd.util.annotation.RpcService;
import org.springframework.beans.factory.annotation.Autowired;
import recipe.aop.LogRecord;
import recipe.atop.BaseAtop;
import recipe.constant.ErrorCode;
import recipe.core.api.doctor.ITherapyRecipeBusinessService;
import recipe.util.ObjectCopyUtils;
import recipe.vo.doctor.RecipeInfoVO;
import recipe.vo.doctor.RecipeTherapyVO;
import recipe.vo.second.OrganVO;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @description： 诊疗处方运营平台入口
 * @author： whf
 * @date： 2022-01-06 9:39
 */
@RpcBean("therapyRecipeGMAtop")
public class TherapyRecipeGMAtop extends BaseAtop {

    @Autowired
    private ITherapyRecipeBusinessService therapyRecipeBusinessService;

    @RpcService
    @LogRecord
    public QueryResult<RecipeTherapyOpVO> findTherapyByInfo(RecipeTherapyOpQueryVO recipeTherapyOpQueryVO) {
        logger.info("TherapyRecipeGMAtop findTherapyByInfo recipeTherapyOpQueryVO={}", JSONUtils.toString(recipeTherapyOpQueryVO));
        validateAtop(recipeTherapyOpQueryVO);
        //越权校验
        if (recipeTherapyOpQueryVO.getOrganId() != null) {
            isAuthorisedOrgan(recipeTherapyOpQueryVO.getOrganId());
        }

        try {
            RecipeTherapyOpQueryDTO recipeTherapyOpQueryDTO = ObjectCopyUtils.convert(recipeTherapyOpQueryVO, RecipeTherapyOpQueryDTO.class);
            QueryResult<RecipeTherapyOpDTO> queryResult = therapyRecipeBusinessService.findTherapyByInfo(recipeTherapyOpQueryDTO);
            List<RecipeTherapyOpDTO> items = queryResult.getItems();
            List<RecipeTherapyOpVO> records = new ArrayList<>();
            for (RecipeTherapyOpDTO item : items) {
                RecipeTherapyOpVO recipeTherapyOpVO = ObjectCopyUtils.convert(item, RecipeTherapyOpVO.class);
                records.add(recipeTherapyOpVO);
            }
            QueryResult<RecipeTherapyOpVO> result = new QueryResult<>();
            if (queryResult.getProperties() != null) {
                Map<String, Object> properties = queryResult.getProperties();
                for (Map.Entry<String, Object> entry : properties.entrySet()) {
                    String key = entry.getKey();
                    result.setProperty(key, entry.getValue());
                }
            }
            result.setItems(records);
            result.setLimit((int) queryResult.getLimit());
            result.setStart(queryResult.getStart());
            result.setTotal(queryResult.getTotal());
            return result;
        } catch (DAOException e1) {
            logger.error("TherapyRecipeOpenAtop findTherapyByInfo error", e1);
            throw new DAOException(ErrorCode.SERVICE_ERROR, e1.getMessage());
        } catch (Exception e) {
            logger.error("TherapyRecipeOpenAtop findTherapyByInfo error e", e);
            throw new DAOException(ErrorCode.SERVICE_ERROR, e.getMessage());
        }

    }

    /**
     * 获取诊疗处方明细
     *
     * @param recipeId 处方id
     * @return
     */
    @RpcService
    public RecipeInfoVO therapyRecipeInfo(Integer recipeId) {
        validateAtop(recipeId);
        RecipeInfoDTO result = therapyRecipeBusinessService.therapyRecipeInfo(recipeId);
        RecipeInfoVO recipeInfoVO = new RecipeInfoVO();
        recipeInfoVO.setPatientVO(ObjectCopyUtils.convert(result.getPatientBean(), PatientVO.class));
        recipeInfoVO.setRecipeBean(ObjectCopyUtils.convert(result.getRecipe(), RecipeBean.class));
        recipeInfoVO.setRecipeExtendBean(ObjectCopyUtils.convert(result.getRecipeExtend(), RecipeExtendBean.class));
        recipeInfoVO.setRecipeDetails(ObjectCopyUtils.convert(result.getRecipeDetails(), RecipeDetailBean.class));
        recipeInfoVO.setRecipeTherapyVO(ObjectCopyUtils.convert(result.getRecipeTherapy(), RecipeTherapyVO.class));
        recipeInfoVO.setOrganVO(ObjectCopyUtils.convert(result.getOrgan(), OrganVO.class));
        logger.info("TherapyRecipeGMAtop therapyRecipeInfo  recipeInfoVO = {}", JSON.toJSONString(recipeInfoVO));
        return recipeInfoVO;
    }
}
