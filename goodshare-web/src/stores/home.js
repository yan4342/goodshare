import { reactive, readonly } from 'vue'

const state = reactive({
  posts: [],
  page: 1,
  activeTag: '推荐',
  scrollTop: 0,
  hasMore: true
})

const setPosts = (posts) => {
  state.posts = posts
}

const appendPosts = (newPosts) => {
  state.posts = [...state.posts, ...newPosts]
}

const setPage = (page) => {
  state.page = page
}

const setActiveTag = (tag) => {
  state.activeTag = tag
}

const setScrollTop = (top) => {
  state.scrollTop = top
}

const setHasMore = (val) => {
  state.hasMore = val
}

const updatePost = (updatedPost) => {
    const index = state.posts.findIndex(p => p.id === updatedPost.id)
    if (index !== -1) {
        // Merge updates
        state.posts[index] = { ...state.posts[index], ...updatedPost }
    }
}

const reset = () => {
    state.posts = []
    state.page = 1
    state.scrollTop = 0
    state.hasMore = true
}

export default {
  state: readonly(state),
  setPosts,
  appendPosts,
  setPage,
  setActiveTag,
  setScrollTop,
  setHasMore,
  updatePost,
  reset
}
