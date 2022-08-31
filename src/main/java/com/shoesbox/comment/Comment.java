package com.shoesbox.comment;

import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import javax.validation.constraints.NotBlank;

@Getter
@NoArgsConstructor
@Entity(name="comment")
public class Comment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false)
    private String nickname;

    @NotBlank
    @Column(nullable = false)
    private String content;

    @NotBlank
    @Column(nullable = false)
    private Long memberId;

    @NotBlank
    @Column(nullable = false)
    private Long postId;

    public Comment(Long postId, CommentRequestDto commentRequestDto){
        this.nickname = commentRequestDto.getNickname();
        this.content = commentRequestDto.getContent();
        this.memberId = commentRequestDto.getMemberId();
        this.postId = postId;
    }

    public void update(CommentRequestDto commentRequestDto){
        this.nickname = commentRequestDto.getNickname();
        this.content = commentRequestDto.getContent();
    }
}
