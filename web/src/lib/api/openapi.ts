import axios, { AxiosInstance } from 'axios';
import { api_host } from '@/config';

const baseURL = !api_host
    ? '/'
    : typeof window !== 'undefined'
        ? `${window.location.protocol}//${api_host}`
        : `http://${api_host}`;

export const openapi: AxiosInstance = axios.create({
    baseURL: baseURL,
    headers: {
        'X-BELLA-CONSOLE': true
    },
    withCredentials: true
});

openapi.interceptors.response.use(
    (response) => {
        if (response.data.code === 200) {
            return response.data || null;
        } else {
            console.error('Failed to fetch api:', response.data.message);
            return Promise.reject({ error: response.data.message });
        }
    },
    (error) => {
        if (error.response && error.response.status === 401) {
            let loginUrl = error.response.headers['X-Redirect-Login'] || error.response.headers['x-redirect-login'];
            if (loginUrl) {
                loginUrl += encodeURIComponent(window.location.href);
                window.location.href = loginUrl;
                return new Promise(() => {});
            }
        }
        return Promise.reject(error);
    }
);
