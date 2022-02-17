package com.example.controller;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.annotation.AccessLimit;
import com.example.annotation.VisitLogger;
import com.example.common.lang.Result;
import com.example.common.lang.vo.PageComment;
import com.example.entity.Comment;
import com.example.service.CommentService;
import com.example.service.MailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authz.annotation.RequiresAuthentication;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.apache.shiro.authz.annotation.RequiresRoles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 服务控制器
 *
 * @author giaming
 * @since 2022-02-12 18:04:11
 * @description 由 Mybatisplus Code Generator 创建
 */
@Slf4j
@RequiredArgsConstructor
@RestController
//@RequestMapping("/comment")
public class CommentController {
    private final CommentService commentService;

    private MailService mailService;

    Logger logger = LoggerFactory.getLogger(CommentController.class);

    /**
     * 分页查询所有评论
     */
    @RequiresAuthentication
    @RequiresPermissions("user:read")
    @GetMapping("/commentList")
    public Result getCommentListByPage(@RequestParam(defaultValue = "1") Integer currentPage,
                                       @RequestParam(defaultValue = "10") Integer pageSize){
        Page page = new Page(currentPage, pageSize);
        IPage pageData = commentService.page(page, new QueryWrapper<Comment>().orderByDesc("create_time"));
        return Result.succ(pageData);
    }

    /**
     * 分页查询某个博客下的根评论
     */
    @RequiresAuthentication
    @RequiresPermissions("user:read")
    @GetMapping("/comment/detail")
    public Result getCommentListByPageId(@RequestParam(defaultValue = "1") Long blogId,
                                         @RequestParam(defaultValue = "1") Integer currentPage,
                                         @RequestParam(defaultValue = "10") Integer pageSize ) {

        Page page = new Page(currentPage, pageSize);
        IPage pageData = commentService.page(page, new QueryWrapper<Comment>().
                eq("blog_id",blogId).orderByDesc("create_time"));

        return Result.succ(pageData);
    }

    /**
     * 获取某个博客下的所有评论
     */
    @GetMapping("/comment/{blogId}")
    public Result getCommentByBlogId(@PathVariable(name = "blogId") Long blogId) {

        //实体模型集合对象转换为VO对象集合
        List<PageComment> pageComments = commentService.getPageCommentListByDesc(blogId, (long) -1);

        for (PageComment pageComment : pageComments) {

            List<PageComment> reply = commentService.getPageCommentList(blogId, pageComment.getId());
            pageComment.setReplyComments(reply);
        }
        return Result.succ(pageComments);
    }


    /**
     * 修改评论的状态
     */
    @RequiresAuthentication
    @RequiresPermissions("user:update")
    @RequestMapping("/comment/publish/{id}")
    public Result publish(@PathVariable(name = "id")String id){
        Comment comment = commentService.getById(id);
        comment.setIsPublished(!comment.getIsPublished());
        commentService.saveOrUpdate(comment);
        return Result.succ(null);
    }


    /**
     * 修改评论
     */
    @RequiresAuthentication
    @RequiresPermissions("user:update")
    @RequestMapping(value = "/comment/update", method = RequestMethod.POST)
    public Result updateById(@Validated @RequestBody Comment comment) {
        if (comment == null) {
            return Result.fail("不能为空");
        }
        commentService.saveOrUpdate(comment);
        return Result.succ(null);
    }


    /**
     * 删除评论
     */
    @RequiresRoles("role_root")
    @RequiresPermissions("user:delete")
    @RequiresAuthentication
    @RequestMapping("/comment/delete{id}")
    public Result delete(@PathVariable(name = "id") String id) {
        if (commentService.removeById(id)){
            return Result.succ(null);
        }else {
            return Result.fail("删除失败");
        }
    }


    /**
     * 提交评论
     */
    @AccessLimit(seconds = 30, maxCount = 1, msg = "30s内只能提交一次评论")
    @VisitLogger(behavior = "提交评论")
    @PostMapping("/comment/add")
    public Result edit(@Validated @RequestBody Comment comment, HttpServletRequest request){
        if (comment.getContent().contains("<script>") || comment.getEmail().contains("<script>")
                || comment.getNickname().contains("<script>") || comment.getWebsite().contains("<script>")) {
            return Result.fail("非法输入");
        }
        System.out.println("comment.toString() = " + comment.toString());
        Comment temp = new Comment();
        temp.setCreateTime(LocalDateTime.now());
        temp.setIp(request.getHeader("x-forwarded-for"));
        BeanUtil.copyProperties(comment, temp, "id", "ip", "createTime");
        commentService.saveOrUpdate(temp);

        //博主的回复向被回复者发送提示邮件
        if(comment.getIsAdminComment()==1&&comment.getParentCommentId()!=-1){
            Comment parentComment = commentService.getOne(new QueryWrapper<Comment>().eq("nickname", comment.getParentCommentNickname()));
            String regex = "\\w+([-+.]\\w+)*@\\w+([-.]\\w+)*\\.\\w+([-.]\\w+)*";
            if (parentComment.getEmail().matches(regex)) {
                mailService.sendSimpleMail(parentComment.getEmail(), "M博客评论回复", "您的的评论："+parentComment.getContent()+"\n博主回复内容："+comment.getContent());
                logger.info("邮件发送成功");
            }
        }
        return Result.succ(null);
    }


}