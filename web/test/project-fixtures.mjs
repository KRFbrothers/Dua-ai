// Synthetic projects belong to tests only; production defaults contain only the hidden Home workspace.
import { defaultProjects } from '../src/core/projects.mjs';
export const testProjects = () => [...defaultProjects(),
  { id: 'coding', name: 'Coding', instructions: '', archived: false },
  { id: 'business', name: 'Business', instructions: '', archived: false }];
