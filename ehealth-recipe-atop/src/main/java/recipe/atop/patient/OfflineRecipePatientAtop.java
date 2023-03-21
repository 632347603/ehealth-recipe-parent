package recipe.atop.patient;

import com.ngari.recipe.offlinetoonline.model.*;
import com.ngari.recipe.recipe.model.MergeRecipeVO;
import ctd.persistence.exception.DAOException;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcBean;
import ctd.util.annotation.RpcService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import recipe.atop.BaseAtop;
import recipe.constant.ErrorCode;
import recipe.core.api.patient.IOfflineRecipeBusinessService;
import recipe.vo.patient.RecipeGiveModeButtonRes;

import javax.validation.Valid;
import java.util.List;


/**
 * @Author liumin
 * @Date 2021/07/06 上午11:42
 * @Description 线下转线上服务入口类
 */
@RpcBean(value = "offlineToOnlineAtop")
@Validated
public class OfflineRecipePatientAtop extends BaseAtop {

    @Autowired
    IOfflineRecipeBusinessService offlineToOnlineService;

    /**
     * 获取线下处方列表
     *
     * @param request 列表查询参数对象
     * @return
     */
    @RpcService
    @Validated
    public List<MergeRecipeVO> findHisRecipeList(FindHisRecipeListVO request) {
        logger.info("OfflineToOnlineAtop findHisRecipeList request:{}", JSONUtils.toString(request));
        validateAtop(request.getOrganId(), request.getMpiId(), request.getStatus());
        try {
            List<MergeRecipeVO> mergeRecipeVOS = offlineToOnlineService.findHisRecipeList(request);
            logger.info("OfflineToOnlineAtop res mergeRecipeVOS:{}", JSONUtils.toString(mergeRecipeVOS));
            return mergeRecipeVOS;
        } catch (DAOException e) {
            logger.error("OfflineToOnlineAtop findHisRecipeList error", e);
            throw new DAOException(e.getCode(), e.getMessage());
        } catch (Exception e) {
            logger.error("OfflineToOnlineAtop findHisRecipeList error", e);
            throw new DAOException(ErrorCode.SERVICE_ERROR, e.getMessage());
        }
    }

    /**
     * 获取线下处方详情
     *
     * @param request
     * @return
     */
    @RpcService
    public FindHisRecipeDetailResVO findHisRecipeDetail(FindHisRecipeDetailReqVO request) {
        logger.info("OfflineToOnlineAtop findHisRecipeDetail request:{}", ctd.util.JSONUtils.toString(request));
        validateAtop(request, request.getOrganId(), request.getMpiId());
        try {
            FindHisRecipeDetailResVO findHisRecipeDetailResVO = offlineToOnlineService.findHisRecipeDetail(request);
            logger.info("OfflineToOnlineAtop findHisRecipeDetail res findHisRecipeDetailResVO:{}", ctd.util.JSONUtils.toString(findHisRecipeDetailResVO));
            return findHisRecipeDetailResVO;
        } catch (DAOException e) {
            logger.error("OfflineToOnlineAtop findHisRecipeDetail error", e);
            throw new DAOException(e.getCode(), e.getMessage());
        } catch (Exception e) {
            logger.error("OfflineToOnlineAtop findHisRecipeDetail error", e);
            throw new DAOException(ErrorCode.SERVICE_ERROR, e.getMessage());
        }
    }

    /**
     * @param request
     * @return
     * @Description 线下处方点够药、缴费点结算 1、线下转线上 2、获取购药按钮
     * @Author liumin
     */
    @RpcService
    @Validated
    public List<RecipeGiveModeButtonRes> settleForOfflineToOnline(@Valid SettleForOfflineToOnlineVO request) {
        logger.info("OfflineToOnlineAtop settleForOfflineToOnline request={}", JSONUtils.toString(request));
        validateAtop(request, request.getRecipeCode(), request.getOrganId(), request.getBusType(), request.getMpiId());
        try {
            List<RecipeGiveModeButtonRes> result = offlineToOnlineService.settleForOfflineToOnline(request);
            logger.info("OfflineToOnlineAtop settleForOfflineToOnline result = {}", JSONUtils.toString(result));
            return result;
        } catch (DAOException e1) {
            logger.error("OfflineToOnlineAtop settleForOfflineToOnline error", e1);
            throw new DAOException(e1.getCode(), e1.getMessage());
        } catch (Exception e) {
            logger.error("OfflineToOnlineAtop settleForOfflineToOnline error e", e);
            throw new DAOException(ErrorCode.SERVICE_ERROR, e.getMessage());
        }

    }

    /**
     * 获取卡类型
     *
     * @param organId
     * @return
     */
    @RpcService
    public List<String> getCardType(Integer organId) {
        logger.info("OfflineToOnlineAtop getCardType request={}", JSONUtils.toString(organId));
        validateAtop(organId);
        try {
            List<String> res = offlineToOnlineService.getCardType(organId);
            logger.info("OfflineToOnlineAtop getCardType result = {}", JSONUtils.toString(res));
            return res;
        } catch (DAOException e1) {
            logger.error("OfflineToOnlineAtop getCardType error", e1);
            throw new DAOException(e1.getCode(), e1.getMessage());
        } catch (Exception e) {
            logger.error("OfflineToOnlineAtop getCardType error e", e);
            throw new DAOException(ErrorCode.SERVICE_ERROR, e.getMessage());
        }

    }

    /**
     * 线下转线上
     *
     * @param request
     * @return
     */
    @RpcService
    public OfflineToOnlineResVO offlineToOnline(OfflineToOnlineReqVO request) {
        logger.info("OfflineToOnlineAtop offlineToOnline request:{}", JSONUtils.toString(request));
        validateAtop(request, request.getOrganId(), request.getMpiId());
        try {
            OfflineToOnlineResVO res = offlineToOnlineService.offlineToOnline(request);
            logger.info("OfflineToOnlineAtop offlineToOnline res findHisRecipeDetailResVO:{}", JSONUtils.toString(res));
            return res;
        } catch (DAOException e) {
            logger.error("OfflineToOnlineAtop findHisRecipeDetail error", e);
            throw new DAOException(e.getCode(), e.getMessage());
        } catch (Exception e) {
            logger.error("OfflineToOnlineAtop findHisRecipeDetail error", e);
            throw new DAOException(ErrorCode.SERVICE_ERROR, e.getMessage());
        }
    }


    /**
     * 线下转线上
     *
     * @param request
     * @return
     */
    @RpcService
    public List<OfflineToOnlineResVO> batchOfflineToOnline(OfflineToOnlineReqVO request) {
        logger.info("OfflineToOnlineAtop batchOfflineToOnline request:{}", JSONUtils.toString(request));
        validateAtop(request, request.getOrganId(), request.getMpiId());
        try {
            List<OfflineToOnlineResVO> res = offlineToOnlineService.batchOfflineToOnline(request);
            logger.info("OfflineToOnlineAtop batchOfflineToOnline res findHisRecipeDetailResVO:{}", JSONUtils.toString(res));
            return res;
        } catch (DAOException e) {
            logger.error("OfflineToOnlineAtop findHisRecipeDetail error", e);
            throw new DAOException(e.getCode(), e.getMessage());
        } catch (Exception e) {
            logger.error("OfflineToOnlineAtop findHisRecipeDetail error", e);
            throw new DAOException(ErrorCode.SERVICE_ERROR, e.getMessage());
        }
    }

}
