import { api } from '@appdeploy/client';
import { installConnection } from '../src/api';
import { createAppDeployConnection } from '../src/appdeploy-connection';

installConnection(createAppDeployConnection(api));
void import('../src/main');
