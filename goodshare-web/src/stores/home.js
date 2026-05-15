import { defineStore } from 'pinia'

export const useHomeStore = defineStore('home', {
  state: () => ({
    posts: [],
    page: 1,
    activeTag: '推荐',
    scrollTop: 0,
    hasMore: true
  }),

  actions: {
    setPosts(posts) {
      this.posts = posts
    },

    appendPosts(newPosts) {
      this.posts = [...this.posts, ...newPosts]
    },

    setPage(page) {
      this.page = page
    },

    setActiveTag(tag) {
      this.activeTag = tag
    },

    setScrollTop(top) {
      this.scrollTop = top
    },

    setHasMore(val) {
      this.hasMore = val
    },

    updatePost(updatedPost) {
      const index = this.posts.findIndex(p => p.id === updatedPost.id)
      if (index !== -1) {
        this.posts[index] = { ...this.posts[index], ...updatedPost }
      }
    },

    removePost(postId) {
      this.posts = this.posts.filter(post => post.id !== postId)
    },

    reset() {
      this.posts = []
      this.page = 1
      this.scrollTop = 0
      this.hasMore = true
    }
  }
})
