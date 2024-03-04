package com.caixy.userservice.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.caixy.common.common.ErrorCode;
import com.caixy.common.constant.CommonConstant;
import com.caixy.common.constant.UserConstant;
import com.caixy.common.exception.BusinessException;
import com.caixy.common.utils.EncryptionUtils;
import com.caixy.common.utils.SqlUtils;
import com.caixy.model.dto.user.AboutMeDTO;
import com.caixy.model.dto.user.UserLoginRequest;
import com.caixy.model.dto.user.UserQueryRequest;
import com.caixy.model.dto.user.UserSearchRequest;
import com.caixy.model.entity.User;
import com.caixy.model.enums.UserRoleEnum;
import com.caixy.model.vo.department.UserDepartmentMajorVO;
import com.caixy.model.vo.user.*;
import com.caixy.userservice.mapper.UserMapper;
import com.caixy.userservice.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author CAIXYPROMISE
 * @description 针对表【user(用户)】的数据库操作Service实现
 * @createDate 2024-02-06 23:22:54
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
        implements UserService
{
    /**
     * 盐值，混淆密码
     */
    public static final String SALT = "caixy";

    @Override
    public Boolean validateUserByIds(List<Long> userIds)
    {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.in("userAccount", userIds);
        return this.count(queryWrapper) > 0;
    }

    @Override
    public long userRegister(String userAccount, String userPassword)
    {
        // 1. 校验
        if (StringUtils.isAnyBlank(userAccount, userPassword))
        {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4)
        {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号过短");
        }
        if (userPassword.length() < 8)
        {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户密码过短");
        }
        return this.makeRegister(userAccount, userPassword);
    }

    @Override
    public User userLogin(UserLoginRequest userLoginRequest, HttpServletRequest request)
    {
        // 0. 提取参数
        String userAccount = userLoginRequest.getUserAccount();
        String userPassword = userLoginRequest.getUserPassword();
//        String captcha = userLoginRequest.getCaptcha().trim();
//        String captchaId = userLoginRequest.getCaptchaId();
        // 1. 校验
        // 1.1 检查参数是否完整
        if (StringUtils.isAnyBlank(userAccount, userPassword))
        {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4)
        {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号错误");
        }

        // 1.2 校验验证码

        // 2. 根据账号查询用户是否存在
        // 查询用户是否存在
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userAccount);
        User user = baseMapper.selectOne(queryWrapper);
        // 用户不存在
        if (user == null)
        {
            log.info("user login failed, userAccount cannot match userPassword");
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在或密码错误");
        }
        if (!EncryptionUtils.matches(userPassword, user.getUserPassword()))
        {
            log.info("user login failed, userAccount cannot match userPassword");
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在或密码错误");
        }
        // 3. 记录用户的登录态
        User userVo = new User();
        BeanUtils.copyProperties(user, userVo);
        request.getSession().setAttribute(UserConstant.USER_LOGIN_STATE, userVo);
        // 登录成功
        return userVo;
    }

    /**
     * 获取当前登录用户
     *
     * @param request
     * @return
     */
    @Override
    public User getLoginUser(HttpServletRequest request)
    {
        // 先判断是否已登录
        Object userObj = request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        User currentUser = (User) userObj;
        if (currentUser == null || currentUser.getId() == null)
        {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return currentUser;
    }

    /**
     * 获取当前登录用户（允许未登录）
     *
     * @param request
     * @return
     */
    @Override
    public User getLoginUserPermitNull(HttpServletRequest request)
    {
        // 先判断是否已登录
        Object userObj = request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        User currentUser = (User) userObj;
        if (currentUser == null || currentUser.getId() == null)
        {
            return null;
        }
        return currentUser;
    }

    /**
     * 是否为管理员
     *
     * @param request
     * @return
     */
    @Override
    public boolean isAdmin(HttpServletRequest request)
    {
        // 仅管理员可查询
        Object userObj = request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        User user = (User) userObj;
        return isAdmin(user);
    }

    @Override
    public boolean isAdmin(User user)
    {
        return user != null && UserRoleEnum.ADMIN.getValue().equals(user.getUserRole());
    }

    /**
     * 用户注销
     *
     * @param request
     */
    @Override
    public boolean userLogout(HttpServletRequest request)
    {
        if (request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE) == null)
        {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "未登录");
        }
        // 移除登录态
        request.getSession().removeAttribute(UserConstant.USER_LOGIN_STATE);
        return true;
    }

    @Override
    public LoginUserVO getLoginUserVO(User user)
    {
        if (user == null)
        {
            return null;
        }
        LoginUserVO loginUserVO = new LoginUserVO();
        BeanUtils.copyProperties(user, loginUserVO);
        return loginUserVO;
    }

    @Override
    public UserVO getUserVO(User user)
    {
        if (user == null)
        {
            return null;
        }
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);
        return userVO;
    }

    @Override
    public List<UserVO> getUserVO(List<User> userList)
    {
        if (CollUtil.isEmpty(userList))
        {
            return new ArrayList<>();
        }
        return userList.stream().map(this::getUserVO).collect(Collectors.toList());
    }

    @Override
    public QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest)
    {
        if (userQueryRequest == null)
        {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = userQueryRequest.getId();
        String unionId = userQueryRequest.getUnionId();
        String mpOpenId = userQueryRequest.getMpOpenId();
        String userName = userQueryRequest.getUserName();
        String userProfile = userQueryRequest.getUserProfile();
        String userRole = userQueryRequest.getUserRole();
        String sortField = userQueryRequest.getSortField();
        String sortOrder = userQueryRequest.getSortOrder();
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(id != null, "id", id);
        queryWrapper.eq(StringUtils.isNotBlank(unionId), "unionId", unionId);
        queryWrapper.eq(StringUtils.isNotBlank(mpOpenId), "mpOpenId", mpOpenId);
        queryWrapper.eq(StringUtils.isNotBlank(userRole), "userRole", userRole);
        queryWrapper.like(StringUtils.isNotBlank(userProfile), "userProfile", userProfile);
        queryWrapper.like(StringUtils.isNotBlank(userName), "userName", userName);
        queryWrapper.orderBy(SqlUtils.validSortField(sortField), sortOrder.equals(CommonConstant.SORT_ORDER_ASC),
                sortField);
        return queryWrapper;
    }

    @Override
    public Long makeRegister(String userAccount, String userPassword)
    {
        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(userPassword);
        return this.makeRegister(user);
    }

    @Override
    public Long makeRegister(User user)
    {
        synchronized (user.getUserAccount().intern())
        {
            // 检查账户是否重复
            checkUserAccount(user.getUserAccount());

            // 加密密码并设置
            String encryptPassword = EncryptionUtils.encodePassword(user.getUserPassword());
            user.setUserPassword(encryptPassword);

            // 插入数据
            boolean saveResult = this.save(user);
            if (!saveResult)
            {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "注册失败，数据库错误");
            }
            return user.getId();
        }
    }

    @Override
    public Page<UserDepartmentMajorVO> listUserWithDepartmentMajor(long current, long size)
    {
        return this.baseMapper.listUserDetailsByPage(new Page<>(current, size));
    }

    @Override
    public UserWorkVO getUserWorkVO(long userId)
    {
        return this.baseMapper.getUserWorkVO(userId);
    }

    @Override
    public List<SearchUserVO> listSearchUserVO(UserSearchRequest payload)
    {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();

        // 根据用户姓名和学号模糊查询
        // 使用嵌套SQL以实现(userName LIKE ? OR userAccount LIKE ?)的逻辑
        if (StringUtils.isNotBlank(payload.getUseKeyword())) {
            queryWrapper.and(wrapper ->
                    wrapper.like("userName", payload.getUseKeyword())
                            .or()
                            .like("userAccount", payload.getUseKeyword())
            );
        }
        // 查找老师 - 学生
        if (StringUtils.isNotBlank(payload.getUserRole()))
        {
            queryWrapper.eq("userRole", payload.getUserRole());
        }

        // 筛选用户的学院/专业ID不在提供的列表中
        if (payload.getUserPermissionIds() != null && !payload.getUserPermissionIds().isEmpty())
        {
            queryWrapper.notIn("userDepartment", payload.getUserPermissionIds());
        }

        List<User> userList = this.baseMapper.selectList(queryWrapper);
        return userList.stream().map(SearchUserVO::of).collect(Collectors.toList());
    }

    @Override
    public List<User> getByAccounts(List<String> userAccount)
    {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.in("userAccount", userAccount);
        return this.baseMapper.selectList(queryWrapper);
    }

    @Override
    public User getByAccount(String userAccount)
    {
        List<User> byAccounts = getByAccounts(Collections.singletonList(userAccount));
        return CollectionUtils.isEmpty(byAccounts) ? null : byAccounts.get(0);
    }

    @Override
    public List<UserWorkVO> getUserWorksByIds(List<Long> userIds)
    {
        if (userIds.isEmpty())
        {
            // 处理空列表的情况，可能是返回空集合或其他逻辑
            return Collections.emptyList();
        }
        List<UserWorkVO> userWorkVOList = this.baseMapper.getUserWorkVOList(userIds);
        log.info("userWorkVOList: {}", userWorkVOList);
        return userWorkVOList;
    }

    // 私有方法，用于检查账户是否重复
    private void checkUserAccount(String userAccount)
    {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userAccount);
        long count = this.baseMapper.selectCount(queryWrapper);
        if (count > 0)
        {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号重复");
        }
    }

    @Override
    public AboutMeVO getAboutMe(Long userId)
    {
        AboutMeDTO aboutMeDto = this.baseMapper.getAboutMe(userId);
        log.info("aboutMeDto: {}", aboutMeDto);
        AboutMeVO aboutMeVO = AboutMeVO.of(aboutMeDto);
        log.info("aboutMeVO: {}", aboutMeVO);
        return aboutMeVO;
    }
}




