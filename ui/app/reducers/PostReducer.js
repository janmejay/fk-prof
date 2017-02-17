import {
  GET_POSTS_REQUEST,
  GET_POSTS_SUCCESS,
  GET_POSTS_FAILURE,
} from 'actions/PostActions';

const INITIAL_STATE = {
  posts: [],
  asyncStatus: 'INIT',
};

export default function (state = INITIAL_STATE, action) {
  switch (action.type) {
    case GET_POSTS_REQUEST:
      return { ...state, asyncStatus: 'PENDING' };

    case GET_POSTS_SUCCESS:
      return { posts: action.data, asyncStatus: 'SUCCESS' };

    case GET_POSTS_FAILURE:
      return { ...state, asyncStatus: 'ERROR' };

    default: return state;
  }
}
