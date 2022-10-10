package recipe.atop;

import ctd.persistence.exception.DAOException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import recipe.constant.ErrorCode;
import recipe.core.api.IEnterpriseBusinessService;
import recipe.core.api.IOrganBusinessService;
import recipe.core.api.doctor.IDoctorBusinessService;
import recipe.core.api.patient.IPatientBusinessService;
import recipe.util.ValidateUtil;

/**
 * atop基类
 *
 * @author fuzi
 */
public class BaseAtop {
    protected final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    protected IDoctorBusinessService iDoctorBusinessService;
    @Autowired
    protected IOrganBusinessService organBusinessService;
    @Autowired
    protected IEnterpriseBusinessService enterpriseBusinessService;
    @Autowired
    protected IPatientBusinessService recipePatientService;

    /**
     * Atop层 入参参数校验：自定义msg
     *
     * @param msg  报错提示
     * @param args 入参
     */
    protected void validateAtop(String msg, Object... args) {
        if (StringUtils.isEmpty(msg)) {
            validateAtop(args);
        }
        if (ValidateUtil.validateObjects(args)) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, msg);
        }
    }

    /**
     * Atop层 入参参数校验：默认报错
     *
     * @param args 入参
     */
    protected void validateAtop(Object... args) {
        if (ValidateUtil.validateObjects(args)) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "入参错误");
        }
    }


    /**
     * Atop层 入参参数校验：默认报错
     *
     * @param args 入参
     */
    protected void isAuthorisedOrgan(Integer organId) {
        if (ValidateUtil.validateObjects(organId)) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "入参错误");
        }

        organBusinessService.isAuthorisedOrgan(organId);
    }
}
