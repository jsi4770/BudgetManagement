package com.back.was.common;

/** 로그인한 사용자 id 를 꺼내는 창구. 컨트롤러들이 이걸로 userId 를 얻는다 */
public interface CurrentUserProvider {
    Long currentUserId();
}
