import ResourcePage from '../components/ResourcePage';
import { RESOURCE_CONFIGS } from '../config/resources';

export default function Settings() {
    return <ResourcePage config={RESOURCE_CONFIGS.settings} />;
}
