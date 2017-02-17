// you can create different actions files based on your functionality and categorization
import fetch from 'isomorphic-fetch';

export const GET_POSTS_REQUEST = 'GET_POSTS_REQUEST';
export const GET_POSTS_SUCCESS = 'GET_POSTS_SUCCESS';
export const GET_POSTS_FAILURE = 'GET_POSTS_FAILURE';

export function getPostsRequestAction () {
  return { type: GET_POSTS_REQUEST };
}

export function getPostsSuccessAction (posts) {
  return { type: GET_POSTS_SUCCESS, data: posts };
}

export function getPostsFailureAction (error) {
  return { type: GET_POSTS_FAILURE, error };
}

export function fetchPostsAction () {
  return (dispatch) => {
    dispatch(getPostsRequestAction()); // tell we're in loading state untill call succeeds
    // helpful to show loaders in UI

    return fetch('https://jsonplaceholder.typicode.com/posts')
      .then(response => response.json())
      .then(json => dispatch(getPostsSuccessAction(json))) // success, send the data to reducers
      .catch(err => dispatch(getPostsFailureAction(err))); // for error
  };
}
