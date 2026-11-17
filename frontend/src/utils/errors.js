export function getApiErrorMessage(error, fallback = 'Something went wrong. Please try again.') {
  if (error?.response?.data?.message) {
    return error.response.data.message;
  }
  if (error?.code === 'ERR_NETWORK') {
    return 'Cannot reach TRUSTLAYER. Check your connection and try again.';
  }
  return fallback;
}

export function getFieldErrors(error) {
  return error?.response?.data?.fieldErrors ?? {};
}
